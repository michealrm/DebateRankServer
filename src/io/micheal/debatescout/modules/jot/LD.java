package io.micheal.debatescout.modules.jot;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.micheal.debatescout.Debater;
import io.micheal.debatescout.Tournament;
import io.micheal.debatescout.UnsupportedNameException;
import io.micheal.debatescout.helpers.JsoupHelper;
import io.micheal.debatescout.helpers.SQLHelper;
import io.micheal.debatescout.modules.Module;
import io.micheal.debatescout.modules.WorkerPool;

public class LD extends Module {

	// TODO: Multi-thread the tournaments / add a tournament in the thread pool
	
	private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	
	public LD(ArrayList<Tournament> tournaments, SQLHelper sql, WorkerPool manager) {
		super(sql, LogFactory.getLog(LD.class));
		this.tournaments = tournaments;
		this.manager = manager;
	}
	
	public void run() {
		
		Configurations configs = new Configurations();
		Configuration config;
		final boolean overwrite;
		boolean temp;
		try {
			config = configs.properties(new File("config.properties"));
			temp = config.getBoolean("overwrite");
		} catch (ConfigurationException e) {
			log.error(e);
			log.fatal("Could not read config for overwrite boolean. Default false");
			temp = false;
		}
		overwrite = temp;
		
		// Scape events per tournament
		for(Tournament t : tournaments) {
			manager.newModule(new Runnable() {
				public void run() {
					try {
						log.info("JOT Updating " + t.getName());
						Document tPage = JsoupHelper.retryIfTimeout(t.getLink(), 3);
						Elements prelims = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)").select("a[href]:contains(Prelims)");
						long one = System.currentTimeMillis();
						HashMap<String, Debater> competitors = null;
						for(Element prelim : prelims) { // TODO: Add Packet & Elims
							Document p = JsoupHelper.retryIfTimeout(prelim.absUrl("href"), 3);
							Element table = p.select("table[border=1]").first();
							Elements rows = table.select("tr:has(table)");
							
							// Register all debaters
							competitors = new HashMap<String, Debater>();
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
							
							System.out.println(System.currentTimeMillis() - one);
							// Check if we've logged this
							ResultSet tournamentExists = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE absUrl=?", p.baseUri());
							if(tournamentExists.last() && rows.size() > 0) {
								int count = 0;
								for(int i = 0;i<rows.size();i++) {
									Elements cols = rows.get(i).select("td[width=80]");
									for(int k = 0;k<cols.size();k++) {
										Element win = cols.get(k).select("[colspan=2].rec").first();
										Element against = cols.get(k).select("[colspan=2][align=right]").first();
										try {
											if(win.text().equals("F"))
												continue;
											against.text();
											count++;
										}
										catch(Exception e) {}
									}
								}
								if(tournamentExists.getRow() == count) {
									log.info("JOT " + t.getName() + " prelims is up to date.");
									continue;
								}
								
								// Overwrite
								if(overwrite)
									sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", p.baseUri());
							}
							
							// Parse rounds
							String query = "INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, speaks, decision) VALUES ";
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
									a.add(t.getLink());
									a.add(p.baseUri());
									a.add(competitors.get(key).getID());
									if(win.text().equals("F") || win.text().equals("B")) {
										a.add(competitors.get(key).getID());
										a.add(new Character(Character.forDigit(k+1, 10)));
										a.add(null);
										a.add(null);
										a.add(win.text());
										if(win.text().equals("F"))
											continue;
									}
									else {
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
											a.add(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
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
									if(!overwrite) {
										ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND speaks<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6), a.get(7));
										if(!exists.next()) {
											query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?), ";
											args.addAll(a);
										}
									}
									else {
										query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?), ";
										args.addAll(a);
									}
								}
							}
							if(!query.equals("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, speaks, decision) VALUES ")) {
								query = query.substring(0, query.lastIndexOf(", "));
								sql.executePreparedStatement(query, args.toArray());
								log.info("JOT " + t.getName() + " prelims updated.");
							}
							else {
								log.info("JOT " + t.getName() + " prelims is up to date.");
							}
						
						}
						// Double Octos (NEEDS WORK)
						Elements doubleOctos = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)").select("a[href]:contains(Double Octos)");
						if(competitors != null && competitors.size() > 0)
							for(Element dboc : doubleOctos) {
								Document dbocp = JsoupHelper.retryIfTimeout(dboc.absUrl("href"), 3);
								Pattern pattern = Pattern.compile("(.+?) .+?( \\(.+?\\))? \\((Aff|Neg)\\) def. (.+?) .+?( \\(.+?\\))? \\((Aff|Neg)\\)");
								Matcher matcher = pattern.matcher(dbocp.html().replaceAll("\n", ""));
								
								// Check if we've logged this
								int count = 0;
								while(matcher.find())
									count+= 2;
								ResultSet tournamentExists = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE absUrl=?", dbocp.baseUri());
								if(tournamentExists.last() && tournamentExists.getRow() == count) {
									log.info("JOT " + t.getName() + " double octos is up to date.");
								}
								else {
									matcher.reset();
									ArrayList<Object> args = new ArrayList<Object>();
									String query = "INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, speaks, decision) VALUES ";
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", dbocp.baseUri());
									while(matcher.find()) {
										// First debater
										ArrayList<Object> a = new ArrayList<Object>();
										a.add(t.getLink());
										a.add(dbocp.baseUri());
										a.add(competitors.get(matcher.group(1)).getID());
										a.add(competitors.get(matcher.group(4)).getID());
										a.add("DO");
										a.add(matcher.group(3).equals("Aff") ? new Character('A') : new Character('N'));
										a.add(null);
										a.add("1-0");
										if(!overwrite) {
											ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND speaks<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6), a.get(7));
											if(!exists.next()) {
												query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?), ";
												args.addAll(a);
											}
										}
										else {
											query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?), ";
											args.addAll(a);
										}
										
										// Second debater
										a.clear();
										a.add(t.getLink());
										a.add(dbocp.baseUri());
										a.add(competitors.get(matcher.group(4)).getID());
										a.add(competitors.get(matcher.group(1)).getID());
										a.add("DO");
										a.add(matcher.group(6).equals("Aff") ? new Character('A') : new Character('N'));
										a.add(null);
										a.add("0-1");
										if(!overwrite) {
											ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND speaks<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6), a.get(7));
											if(!exists.next()) {
												query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?), ";
												args.addAll(a);
											}
										}
										else {
											query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?), ";
											args.addAll(a);
										}
									}

									System.out.println(query);
									System.out.println(args);
									System.exit(0);
									
									if(!query.equals("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, speaks, decision) VALUES ")) {
										query = query.substring(0, query.lastIndexOf(", "));
										//sql.executePreparedStatement(query, args.toArray());
										log.info("JOT " + t.getName() + " prelims updated.");
									}
									else {
										log.info("JOT " + t.getName() + " prelims is up to date.");
									}
								}
							}
						
						//Elements bracket = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)").select("a[href]:contains(Bracket)");
					} catch(IOException ioe) {
						log.error(ioe);
						log.fatal("Could not update " + t.getName());
					} catch(SQLException sqle) {
						log.error(sqle);
						log.fatal("Could not update " + t.getName() + " - " + sqle.getErrorCode());
					}
				}
			});
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
		if(!index.next())
				return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()));
		else
			return index.getInt(1);
	}

}
