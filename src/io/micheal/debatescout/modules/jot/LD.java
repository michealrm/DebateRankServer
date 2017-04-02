package io.micheal.debatescout.modules.jot;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.micheal.debatescout.Debater;
import io.micheal.debatescout.Module;
import io.micheal.debatescout.SQLHelper;
import io.micheal.debatescout.Tournament;
import io.micheal.debatescout.UnsupportedNameException;

public class LD extends Module {

	// TODO: Multi-thread the tournaments / add a tournament in the thread pool
	
	private ArrayList<Tournament> tournaments;
	
	public LD(ArrayList<Tournament> tournaments, SQLHelper sql, Log log) {
		super(sql, log);
		this.tournaments = tournaments;
	}
	
	public void run() {
		
		// Scape events per tournament
		for(Tournament t : tournaments) {
			try {
				Document tPage = Jsoup.connect(t.getLink()).get();
				Elements lds = tPage.select("a[title~=LD|Lincoln|L-D]"); // TODO: Include tournaments from 2013, which don't contain this attribute (match by table/row)
				for(Element ld : lds)
					if(ld != null && ld.text().equals("Prelims")) { // TODO: Add Packet & Elims
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
							entry.getValue().setID(getDebaterID(entry.getValue()));
						
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
								ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND debater=(SELECT id FROM debaters WHERE id=?) AND against=(SELECT id FROM debaters WHERE id=?) AND round<=>? AND side<=>? AND speaks<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
								if(!exists.next()) {
									query += "((SELECT id FROM tournaments WHERE link=?), (SELECT id FROM debaters WHERE id=?), (SELECT id FROM debaters WHERE id=?), ?, ?, ?, ?), ";
									args.addAll(a);
								}
							}
						}
						if(!query.equals("INSERT INTO ld_rounds (tournament, debater, against, round, side, speaks, decision) VALUES ")) {
							query = query.substring(0, query.lastIndexOf(", "));
							sql.executePreparedStatement(query, args.toArray());
							log.info("JOT " + t.getName() + " updated.");
						}
					}
			} catch(IOException ioe) {
				continue;
			} catch (SQLException sqle) {
				sqle.printStackTrace();
			}
		}
	}
	
	/**
	 * Searches the SQL tables for the specified name. If no match is found, a debater will be created and returned
	 * @return
	 * @throws SQLException 
	 */
	private int getDebaterID(Debater debater) throws SQLException {
		if(debater.getID() != null)
			return debater.getID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id FROM debaters WHERE first_clean<=>? AND middle_clean<=>? AND last_clean<=>? AND surname_clean<=>? AND school_clean<=>?", SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()));
		if(!index.next()) {
			index = sql.executeQueryPreparedStatement("SELECT id FROM debaters WHERE first<=>? AND middle<=>? AND last<=>? AND surname<=>? AND school<=>?", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool());
			if(!index.next())
				return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()));
			else
				return sql.executePreparedStatementArgs("UPDATE debaters SET first_clean<=>?, middle_clean<=>?, last_clean<=>?, surname_clean<=>?, school_clean<=>? WHERE first<=>? AND middle<=>? AND last<=>? AND surname<=>? AND school<=>?", SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()), debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool());
		}
		else
			return index.getInt(1);
	}

}
