package io.micheal.debatescout;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Main {

	public Log log;
	public boolean active = true;
	private Connection sql;
	private String host, name, user, pass;
	private int port;
	private Statement st;
	private Configuration config;
	
	public static void main(String[] args) {
		new Main().run();
	}
	
	public Main() {
		log = LogFactory.getLog(Main.class);
		log.debug("Instantiated logger");

		Configurations configs = new Configurations();
		try
		{
		    config = configs.properties(new File("config.properties"));
		    host = config.getString("db.host");
		    name = config.getString("db.name");
		    user = config.getString("db.username");
		    pass = config.getString("db.password");
		    port = config.getInt("db.port");

			Class.forName("com.mysql.cj.jdbc.Driver");
			sql = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + name + "?user=" + user + "&password=" + pass);
			st = sql.createStatement();
		} catch (Exception e) {
			log.error(e);
			System.exit(1);
		}
	}
	
	public void run() {
		
		while(active) {
			try {
				Thread.sleep(1); // Change to next update time (defined in config)
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			// JOT Results
			try {
				
				// Get seasons so we can iterate through all the tournaments
				Document tlist = Jsoup.connect("http://www.joyoftournaments.com/results.asp").get();
				ArrayList<String> years = new ArrayList<String>();
				for(Element select : tlist.select("select"))
					if(select.attr("name").equals("season"))
						for(Element option : select.select("option"))
							years.add(option.attr("value"));

				// Get all the tournaments
				ArrayList<Tournament> tournaments = new ArrayList<Tournament>();
				for(String year : years) {
					Document tournamentDoc = Jsoup.connect("http://www.joyoftournaments.com/results.asp")
						.data("state","")
						.data("month", "0")
						.data("season", year)
						.post();
					
					Element table = tournamentDoc.select("table.bc").first();
					Elements rows = table.select("tr");
					for(int i = 1;i<rows.size();i++) {
						Elements cols = rows.get(i).select("td");
						tournaments.add(new Tournament(cols.select("a").first().text(), cols.select("a").first().absUrl("href"), cols.select("[align=center]").first().text(), cols.select("[align=right]").first().text()));
					}
				}
				
				// Update DB / Remove cached tournaments from the queue
				log.debug(tournaments.size() + " tournaments scraped from JOT");
				try {
					ResultSet links = executeQuery("SELECT link FROM tournaments");
					ArrayList<String> cachedLinks = new ArrayList<String>();
					while(links.next())
						cachedLinks.add(links.getString(1));

					if(config.getBoolean("useCache"))
						for(int i = 0;i<tournaments.size();i++)
							for(String k : cachedLinks)
								if(tournaments.get(i).getLink().equals(k))
									tournaments.remove(i--);
					String query = "INSERT IGNORE INTO tournaments (name, state, link, date) VALUES ";
					ArrayList<String> args = new ArrayList<String>();
					for(Tournament t : tournaments) {
						query += "(?,?,?,STR_TO_DATE(?, '%m/%d/%Y')), ";
						args.add(t.getName());
						args.add(t.getState());
						args.add(t.getLink());
						args.add(t.getDate());
					}
					query = query.substring(0, query.lastIndexOf(", "));
					String[] argsArr = new String[args.size()];
					for(int i = 0;i<args.size();i++)
						argsArr[i] = args.get(i);
					executePreparedStatement(query, argsArr);
				} catch (SQLException e) {
					e.printStackTrace();
					log.error(e);
					log.fatal("DB could not be updated with JOT tournament info. " + e.getErrorCode());
				}
				
				// Remove duplicates
				for(int i = 0;i<tournaments.size();i++)
					for(int k = 0;k<tournaments.size();k++)
						if(tournaments.get(i).getLink().equals(tournaments.get(k).getLink()) && i != k) {
							tournaments.remove(k);
							i--;
							k--;
						}
				
				log.info(tournaments.size() + " tournaments queued from JOT");
				
				// Scape events per tournament
				for(Tournament t : tournaments) {
					Document tPage = Jsoup.connect(t.getLink()).get();
					Elements lds = tPage.select("a[title~=LD|Lincoln|L-D]");
					for(Element ld : lds)
						if(ld != null && ld.text().equals("Prelims")) { // Add Packet & Elims
							log.info("JOT Updating " + t.getName());
							Document prelim = Jsoup.connect(ld.absUrl("href")).get();
							Element table = prelim.select("table[border=1]").first();
							Elements rows = table.select("tr:has(table)");
							HashMap<String, Debater> competitors = new HashMap<String, Debater>();
							// Register all debaters
							for(Element row : rows) {
								Elements infos = row.select("td").first().select("td");
								try {
									competitors.put(infos.get(2).text(), new Debater(infos.get(3).text(), infos.get(1).text()));
								} catch (UnsupportedNameException e) {
									log.error(e);
								}
							}
							
							// Update DB with debaters
							for(Map.Entry<String, Debater> entry : competitors.entrySet())
								entry.getValue().setID(getOrCreateDebaterID(entry.getValue()));
							
							// Parse rounds
							String query = "INSERT INTO ld_rounds (tournament, debater, against, round, side, speaks, decision) VALUES ";
							ArrayList<Object> args = new ArrayList<Object>();
							for(int i = 0;i<rows.size();i++) {
								String key = rows.get(i).select("td").first().select("td").get(2).text();
								Elements cols = rows.get(i).select("td[width=80]");
								for(int k = 0;k<cols.size();k++) {
									Element speaks = cols.get(k).select("[width=50%][align=left]").first();
									Element side = cols.get(k).select("[width=50%][align=right]").first();
									Element win = cols.get(k).select("[colspan=2].rec").first();
									Element against = cols.get(k).select("[colspan=2][align=right]").first();
									try {
										win.text();
										against.text();
									}
									catch(Exception e) {
										continue;
									}
									ArrayList<Object> a = new ArrayList<Object>();
									if(win.text().equals("F") || win.text().equals("B")) {
										a.add(t.getLink());
										a.add(competitors.get(key).getID());
										a.add(competitors.get(key).getID());
										a.add(null);
										a.add(null);
										a.add(null);
										a.add(win.text());
										if(win.text().equals("F"))
											continue;
									}
									else {
										a.add(t.getLink());
										a.add(competitors.get(key).getID());
										if(against.text() != null && competitors.get(against.text()) != null)
											a.add(competitors.get(against.text()).getID());
										else
											a.add(competitors.get(key).getID());
										a.add(new Character(Character.forDigit(k+1, 10)));
										if(side != null)
											a.add(side.text().equals("Aff") ? new Character('A') : new Character('N'));
										else
											a.add(null);
										try {
											a.add(Double.parseDouble(speaks.text().replaceAll("\\*", "")));
										}
										catch(Exception e) {
											a.add(null);
										}
										if(win.text().equals("W"))
											a.add("1-0");
										else if(win.text().equals("L"))
											a.add("0-1");
										else
											a.add(win.text());
										
									}
									
									// Check if exists
									ResultSet exists = executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND debater=(SELECT id FROM debaters WHERE id=?) AND against=(SELECT id FROM debaters WHERE id=?) AND round<=>? AND side<=>? AND speaks<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
									if(!exists.next()) {
										query += "((SELECT id FROM tournaments WHERE link=?), (SELECT id FROM debaters WHERE id=?), (SELECT id FROM debaters WHERE id=?), ?, ?, ?, ?), ";
										args.addAll(a);
									}
								}
							}
							if(!query.equals("INSERT INTO ld_rounds (tournament, debater, against, round, side, speaks, decision) VALUES ")) {
								query = query.substring(0, query.lastIndexOf(", "));
								executePreparedStatement(query, args.toArray());
								log.info("JOT " + t.getName() + " updated.");
							}
						}
					//System.exit(0);
				}
				
				System.exit(0);
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Searches the SQL tables for the specified name. If no match is found, a debater will be created and returned
	 * @return
	 * @throws SQLException 
	 */
	private int getOrCreateDebaterID(Debater debater) throws SQLException {
		if(debater.getID() != null)
			return debater.getID();
		ResultSet index = executeQueryPreparedStatement("SELECT id FROM debaters WHERE first<=>? AND middle<=>? AND last<=>? AND surname<=>? AND school<=>REPLACE(?, '.', '')", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool());
		if(!index.next())
			return executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school) VALUES (?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool());
		else
			return index.getInt(1);
	}
	
	public static String cleanString(String s) {
		return s.toLowerCase().replaceAll(".|,|'|\"", "");
	}
	
	private ResultSet executeQuery(String query) throws SQLException {
		log.debug("Executing query --> " + query);
		return st.executeQuery(query);
	}
	
	private ResultSet executeQueryPreparedStatement(String query, Object... values) throws SQLException {
		PreparedStatement ps = sql.prepareStatement(query);
		for(int i = 0;i<values.length;i++) {
			if(values[i] instanceof String)
				ps.setString(i+1, (String)values[i]);
			else if(values[i] instanceof Integer)
				ps.setInt(i+1, (Integer)values[i]);
			else if(values[i] instanceof Double)
				ps.setDouble(i+1, (Double)values[i]);
			else if(values[i] instanceof Character)
				ps.setString(i+1, ((Character)values[i]).toString());
			else if(values[i] == null)
				ps.setNull(i+1, Types.NULL);
		}
		log.debug("Executing query --> " + ps);
		return ps.executeQuery();
	}
	
	private int executePreparedStatementArgs(String query, String... values) throws SQLException {
		return executePreparedStatement(query, values);
	}
	
	private int executePreparedStatement(String query, Object[] values) throws SQLException {
		PreparedStatement ps = sql.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
		for(int i = 0;i<values.length;i++) {
			if(values[i] instanceof String)
				ps.setString(i+1, (String)values[i]);
			else if(values[i] instanceof Integer)
				ps.setInt(i+1, (Integer)values[i]);
			else if(values[i] instanceof Double)
				ps.setDouble(i+1, (Double)values[i]);
			else if(values[i] instanceof Character)
				ps.setString(i+1, ((Character)values[i]).toString());
			else if(values[i] == null)
				ps.setNull(i+1, Types.NULL);
		}
		log.debug("Executing --> " + ps);
		ps.executeUpdate();
		ResultSet rs = ps.getGeneratedKeys();
        if(rs.next())
            return rs.getInt(1);
		return -1;
	}
}
