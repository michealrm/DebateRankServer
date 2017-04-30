package io.micheal.debaterank.modules.jot;

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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.Tournament;
import io.micheal.debaterank.UnsupportedNameException;
import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.JsoupHelper;
import io.micheal.debaterank.util.Round;
import io.micheal.debaterank.util.SQLHelper;

public class LD extends Module {

	// TODO: Multi-thread the tournaments / add a tournament in the thread pool
	
	private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	private final boolean overwrite;
	
	public LD(ArrayList<Tournament> tournaments, SQLHelper sql, WorkerPool manager) {
		super(sql, LogManager.getLogger(LD.class));
		this.tournaments = tournaments;
		this.manager = manager;
		
		Configuration config;
		boolean temp;
		try {
			config = new Configurations().properties(new File("config.properties"));
			temp = config.getBoolean("overwrite");
		} catch (ConfigurationException e) {
			log.error(e);
			log.fatal("Could not read config for overwrite boolean. Default false");
			temp = false;
		}
		overwrite = temp;
	}
	
	public void run() {
		
		// Scrape events per tournament
		for(Tournament t : tournaments) {
			manager.newModule(new Runnable() {
				public void run() {
					try {
						log.log(DebateHelper.JOT, "Updating " + t.getName());
						Document tPage = JsoupHelper.retryIfTimeout(t.getLink(), 3);
						Elements eventRows = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)");
						
						for(Element eventRow : eventRows) {
							
							// Prelims
							Element prelim = eventRow.select("a[href]:contains(Prelims)").first();
							if(prelim != null) {
								Document p = JsoupHelper.retryIfTimeout(prelim.absUrl("href"), 3);
								Element table = p.select("table[border=1]").first();
								Elements rows = table.select("tr:has(table)");
								
								// Register all debaters
								HashMap<String, Debater> competitors = new HashMap<String, Debater>();
								for(Element row : rows) {
									Elements infos = row.select("td").first().select("td");
									try {
										competitors.put(infos.get(2).text(), new Debater(infos.get(3).text(), infos.get(1).text()));
									} catch (UnsupportedNameException e) {
										log.error(e);
									}
								}
								
								// If we have the same amount of entries, then do not check
								if(tournamentExists(p.baseUri(), table.select("[colspan=2].rec:not(:containsOwn(F))").size()))
									log.log(DebateHelper.JOT, t.getName() + " prelims is up to date.");
								else {
									// Update DB with debaters
									updateCompetitorsIDs(sql, competitors);
									
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", p.baseUri());
								
									// Parse rounds, TODO: Needs optimizations / profiling
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
												exists.close();
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
										log.log(DebateHelper.JOT, t.getName() + " prelims updated.");
									}
									else {
										log.log(DebateHelper.JOT, t.getName() + " prelims is up to date.");
									}
								}
							}
								
							// Double Octos
							Element doubleOctos = eventRow.select("a[href]:contains(Double Octos)").first();
							if(doubleOctos != null) {
								Document doc = JsoupHelper.retryIfTimeout(doubleOctos.absUrl("href"), 3);
								
								// If we have the same amount of entries, then do not check
								Pattern pattern = Pattern.compile("[^\\s]+ (.+?)( \\((.+?)\\))? \\((Aff|Neg)\\) def. [^\\s]+ (.+?)( \\((.+?)\\))? \\((Aff|Neg)\\)");
								doc.getElementsByTag("font").unwrap();
								Matcher matcher = pattern.matcher(doc.toString().replaceAll("<br>", ""));
								int count = 0;
								while(matcher.find())
									count += 2;
								if(tournamentExists(doc.baseUri(), count))
									log.log(DebateHelper.JOT, t.getName() + " double octos is up to date.");
								else {
									
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", doc.baseUri());
									
									matcher.reset();
									ArrayList<Object> args = new ArrayList<Object>();
									String query = "INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ";
									while(matcher.find()) {
										try {
											// First debater
											ArrayList<Object> a = new ArrayList<Object>();
											a.add(t.getLink());
											a.add(doc.baseUri());
											a.add(DebateHelper.getDebaterID(sql, new Debater(matcher.group(1), matcher.group(3))));
											a.add(DebateHelper.getDebaterID(sql, new Debater(matcher.group(5), matcher.group(7))));
											a.add(Round.DOUBLE_OCTOS);
											a.add(matcher.group(4).equals("Aff") ? new Character('A') : new Character('N'));
											a.add("1-0");
											if(!overwrite) {
												ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
												if(!exists.next()) {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
												exists.close();
											}
											else {
												query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
												args.addAll(a);
											}
											
											// Second debater
											a.clear();
											a.add(t.getLink());
											a.add(doc.baseUri());
											a.add(DebateHelper.getDebaterID(sql, new Debater(matcher.group(5), matcher.group(7))));
											a.add(DebateHelper.getDebaterID(sql, new Debater(matcher.group(1), matcher.group(3))));
											a.add(Round.DOUBLE_OCTOS);
											a.add(matcher.group(8).equals("Aff") ? new Character('A') : new Character('N'));
											a.add("0-1");
											if(!overwrite) {
												ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6), a.get(7));
												if(!exists.next()) {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
											}
											else {
												query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
												args.addAll(a);
											}
										}
										catch(UnsupportedNameException une) {}
									}
	
									if(!query.equals("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ")) {
										query = query.substring(0, query.lastIndexOf(", "));
										sql.executePreparedStatement(query, args.toArray());
										log.log(DebateHelper.JOT, t.getName() + " double octos updated.");
									}
									else {
										log.log(DebateHelper.JOT, t.getName() + " double octos is up to date.");
									}
								}
							}
							
							//Bracket
							Element bracket = eventRow.select("a[href]:contains(Bracket)").first();
							if(bracket != null) {
								Document doc = JsoupHelper.retryIfTimeout(bracket.absUrl("href"), 3);
								
								// If we have the same amount of entries, then do not check
								if(tournamentExists(doc.baseUri(), doc.select("table[cellspacing=0] > tbody > tr > td.botr, table[cellspacing=0] > tbody > tr > td.topr, table[cellspacing=0] > tbody > tr > td.top, table[cellspacing=0] > tbody > tr > td.btm").size() - 1))
									log.log(DebateHelper.JOT, t.getName() + " bracket is up to date.");
								else {
									
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", doc.baseUri());
									
									// Parse rounds
									ArrayList<Object> args = new ArrayList<Object>();
									String query = "INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, decision) VALUES ";
									Round round = null, last = null;
									ArrayList<Pair<Debater, Debater>> matchup = new ArrayList<Pair<Debater, Debater>>();
									for(int i = 0;(round = getBracketRound(doc, i)) != null;i++) {
										
										// Check if this round is the final results
										if(last == Round.FINALS) {
											
											Element element = doc.select("table[cellspacing=0] > tbody > tr > td.top:eq(" + i + ")").first();
											Element debater = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
											if(debater != null) {
												try {
												Debater winner, loser;
												if(matchup.get(0).getLeft().equals(new Debater(debater.text().substring(debater.text().indexOf(' ') + 1), null))) {
													winner = matchup.get(0).getLeft();
													loser = matchup.get(0).getRight();
												}
												else {
													winner = matchup.get(0).getRight();
													loser = matchup.get(0).getLeft();
												}
												
												// Winner
												
												ArrayList<Object> a = new ArrayList<Object>();
												a.add(t.getLink());
												a.add(doc.baseUri());
												a.add(winner.getID());
												a.add(loser.getID());
												a.add(last.toString());
												a.add("1-0");
												
												if(!overwrite) {
													ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5));
													if(!exists.next()) {
														query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
														args.addAll(a);
													}
													exists.close();
												}
												else {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
												
												// Loser
												
												a.clear();
												a.add(t.getLink());
												a.add(doc.baseUri());
												a.add(loser.getID());
												a.add(winner.getID());
												a.add(last.toString());
												a.add("0-1");
												
												if(!overwrite) {
													ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
													if(!exists.next()) {
														query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
														args.addAll(a);
													}
													exists.close();
												}
												else {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
												
												} catch(UnsupportedNameException une) {}
											}
											if(!query.equals("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, decision) VALUES ")) {
												query = query.substring(0, query.lastIndexOf(", "));
												sql.executePreparedStatement(query, args.toArray());
												log.log(DebateHelper.JOT, t.getName() + " bracket updated.");
											}
											else {
												log.log(DebateHelper.JOT, t.getName() + " bracket is up to date.");
											}
											break;
										}
	
										// Add all debaters to an arraylist of pairs
										Elements col = doc.select("table[cellspacing=0] > tbody > tr > td:eq(" + i + ")");
										Element left = null;
										ArrayList<Pair<Debater, Debater>> currentMatchup = new ArrayList<Pair<Debater, Debater>>();
										for(Element element : col) {
											Element debater = null;
											if(element.hasClass("btm") || element.hasClass("botr"))
												debater = element;
											else if(element.hasClass("top") || element.hasClass("topr"))
												debater = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
											else
												continue;
											if(left == null)
												left = debater;
											else {
												String leftSchool = null,
														rightSchool = null,
														leftText = left.childNode(0).toString(),
														rightText = debater.childNode(0).toString();
												if(left.childNodeSize() > 2)
													if(left.childNode(2) instanceof TextNode)
														leftSchool = left.childNode(2).toString();
													else
														leftSchool = left.childNode(2).unwrap().toString();
												if(debater.childNodeSize() > 2)
													if(debater.childNode(2) instanceof TextNode)
														rightSchool = debater.childNode(2).toString();
													else
														rightSchool = debater.childNode(2).unwrap().toString();
												try {
													currentMatchup.add(Pair.of(new Debater(leftText.substring(leftText.indexOf(' ') + 1), leftSchool), new Debater(rightText.substring(rightText.indexOf(' ') + 1), rightSchool)));
												} catch (UnsupportedNameException e) {}
												left = null;
											}
										}
										
										if(matchup != null && last != null) {
											
											// Sort matchups into winner/loser pairs
											ArrayList<Pair<Debater, Debater>> winnerLoser = new ArrayList<Pair<Debater, Debater>>();
											for(Pair<Debater, Debater> winners : currentMatchup)
												for(Pair<Debater, Debater> matchups : matchup)
													if(winners.getLeft().equals(matchups.getLeft()) || winners.getRight().equals(matchups.getLeft()))
														winnerLoser.add(matchups);
													else if(winners.getLeft().equals(matchups.getRight()) || winners.getRight().equals(matchups.getRight()))
														winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));
											
											for(Pair<Debater, Debater> pair : winnerLoser) {
												
												// Winner
												
												ArrayList<Object> a = new ArrayList<Object>();
												a.add(t.getLink());
												a.add(doc.baseUri());
												a.add(pair.getLeft().getID());
												a.add(pair.getRight().getID());
												a.add(last.toString());
												a.add("1-0");
												
												if(!overwrite) {
													ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5));
													if(!exists.next()) {
														query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
														args.addAll(a);
													}
												}
												else {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
												
												// Loser
												
												a.clear();
												a.add(t.getLink());
												a.add(doc.baseUri());
												a.add(pair.getRight().getID());
												a.add(pair.getLeft().getID());
												a.add(last.toString());
												a.add("0-1");
												
												if(!overwrite) {
													ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5));
													if(!exists.next()) {
														query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
														args.addAll(a);
													}
												}
												else {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
											}
										}
										else {
											// Update IDs
											for(Pair<Debater, Debater> pair : currentMatchup) {
												try {
													pair.getLeft().setID(DebateHelper.getDebaterID(sql, pair.getLeft()));
													pair.getRight().setID(DebateHelper.getDebaterID(sql, pair.getRight()));
												} catch (UnsupportedNameException e) {}
											}
										}
										
										last = round;
										matchup = currentMatchup;
									}
								}
							}
						}
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
	 * Updates competitors' IDs if they haven't been updated yet
	 * @throws SQLException
	 */
	private void updateCompetitorsIDs(SQLHelper sql, HashMap<String, Debater> competitors) throws SQLException {
		boolean updated = false;
		for(Map.Entry<String, Debater> entry : competitors.entrySet())
			if(entry.getValue().getID() != null)
				updated = true;
		if(!updated)
			DebateHelper.updateDebaterIDs(sql, competitors);
	}
	
	private boolean tournamentExists(String absUrl, int rounds) throws SQLException {
		ResultSet tournamentExists = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE absUrl=?", absUrl);
		return tournamentExists.last() && tournamentExists.getRow() == rounds;
	}
	
	private Round getBracketRound(Document doc, int col) {
		int sel = doc.select("table[cellspacing=0] > tbody > tr > td.botr:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.topr:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.top:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.btm:eq(" + col + ")").size();
		if(sel % 2 == 0 || sel == 1) {
			switch(sel) {
				case 1:
					return Round.FINALS;
				case 2:
					return Round.FINALS;
				case 4:
					return Round.SEMIS;
				case 8:
					return Round.QUARTERS;
				case 16:
					return Round.OCTOS;
				case 32:
					return Round.DOUBLE_OCTOS;
			}
		}
		return null;
	}

}