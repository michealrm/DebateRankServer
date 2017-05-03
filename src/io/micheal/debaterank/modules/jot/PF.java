package io.micheal.debaterank.modules.jot;

import static io.micheal.debaterank.util.DebateHelper.*;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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
import io.micheal.debaterank.Team;
import io.micheal.debaterank.Tournament;
import io.micheal.debaterank.UnsupportedNameException;
import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.JsoupHelper;
import io.micheal.debaterank.util.Round;
import io.micheal.debaterank.util.SQLHelper;

public class PF extends Module {
	
	private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	private final boolean overwrite;
	
	public PF(ArrayList<Tournament> tournaments, SQLHelper sql, WorkerPool manager) {
		super(sql, LogManager.getLogger(PF.class));
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
						log.log(JOT, "Updating " + t.getName());
						Document tPage = JsoupHelper.retryIfTimeout(t.getLink(), 3);
						Elements eventRows = tPage.select("tr:has(td:matches(PF|Public F|P-F)");

						for(Element eventRow : eventRows) {

							// Prelims
							Element prelim = eventRow.select("a[href]:contains(Prelims)").first();
							if(prelim != null) {
								Document p = JsoupHelper.retryIfTimeout(prelim.absUrl("href"), 3);
								Element table = p.select("table[border=1]").first();
								Elements rows = table.select("tr:has(table)");
								
								// Register all debaters
								HashMap<String, Team> competitors = new HashMap<String, Team>();
								for(Element row : rows) {
									Element info = row.select("td").first();
									try {
										String left = info.select("tr:eq(0)").text();
										String second = info.select("tr:eq(1)").text().replaceAll("\u00a0|&nbsp", " ");
										String key = second.split(" ")[0];
										String school = second.split(" ").length > 1 ? second.substring(second.indexOf(' ') + 1) : null;
										String right = info.select("tr:eq(2)").text();
										competitors.put(key, new Team(new Debater(left, school), new Debater(right, school)));
									} catch (UnsupportedNameException e) {
										log.error(e);
									}
								}
								
								// If we have the same amount of entries, then do not check
								if(tournamentExists(p.baseUri(), table.select("[colspan=2].rec:not(:containsOwn(F))").size(), sql))
									log.log(JOT, t.getName() + " prelims is up to date.");
								else {
									// Update DB with debaters
									updateTeamIDs(sql, competitors, "PF");
									
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM pf_rounds WHERE absUrl=?", p.baseUri());
								
									// Parse rounds
									String query = "INSERT INTO pf_rounds (tournament, absUrl, team, against, round, side, speaks1, speaks2, place1, place2, decision) VALUES ";
									ArrayList<Object> args = new ArrayList<Object>();
									for(int i = 0;i<rows.size();i++) {
										String key = rows.get(i).select("td").first().select("tr:eq(1)").text().replaceAll("\u00a0|&nbsp", " ").split(" ")[0];
										Elements cols = rows.get(i).select("td[width=80]");
										for(int k = 0;k<cols.size();k++) {
											Element top, win, side, against, bot, speaks1, speaks2, place1, place2;
											try {
												top = cols.get(k).select("tr:eq(0)").first();
												speaks1 = top.select("td[align=left]").first();
												against = top.select("td[align=center]").first();
												place1 = top.select("td[align=right]").first();
												
												win = cols.get(k).select(".rec").first();
												
												bot = cols.get(k).select("tr:eq(0)").first();
												speaks2 = bot.select("td[align=left]").first();
												side = bot.select("td[align=center]").first();
												place2 = bot.select("td[align=right]").first();
											
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
												a.add(Character.forDigit(k+1, 10));
												a.add(null);
												a.add(null);
												a.add(null);
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
												a.add(Character.forDigit(k+1, 10));
												if(side != null)
													a.add(side.text().equals("Aff") ? new Character('A') : new Character('N'));
												else
													a.add(null);
												try {
													double speaksOne = Double.parseDouble(speaks1.text().replaceAll("\\\\*", ""));
													double speaksTwo = Double.parseDouble(speaks2.text().replaceAll("\\\\*", ""));
													a.add(speaksOne);
													a.add(speaksTwo);
												}
												catch(Exception e) {
													a.add(null);
													a.add(null);
												}
												try {
													int placeOne = Integer.parseInt(place1.text().replaceAll("\\\\*", ""));
													int placeTwo = Integer.parseInt(place2.text().replaceAll("\\\\*", ""));
													a.add(placeOne);
													a.add(placeTwo);
												}
												catch(Exception e) {
													a.add(null);
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
												ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM pf_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND speaks1<=>? AND speaks2<=>? AND place1<=>? AND place2<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6), a.get(7), a.get(8), a.get(9), a.get(10));
												if(!exists.next()) {
													query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?), ";
													args.addAll(a);
												}
												exists.close();
											}
											else {
												query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?), ";
												args.addAll(a);
											}
										}
									}
									if(!query.equals("INSERT INTO pf_rounds (tournament, absUrl, debater, against, round, side, speaks1, speaks2, place1, place2, decision) VALUES ")) {
										query = query.substring(0, query.lastIndexOf(", "));
										sql.executePreparedStatement(query, args.toArray());
										log.log(JOT, t.getName() + " prelims updated.");
									}
									else {
										log.log(JOT, t.getName() + " prelims is up to date.");
									}
								}
							}
								
							// Double Octos
							Element doubleOctos = eventRow.select("a[href]:contains(Double Octos)").first();
							if(doubleOctos != null) {
								Document doc = JsoupHelper.retryIfTimeout(doubleOctos.absUrl("href"), 3);
								
								// If we have the same amount of entries, then do not check
								Pattern pattern = Pattern.compile("[^\\s]+ ([A-Za-z]+?) - ([A-Za-z]+?)( \\((.+?)\\))? \\((Aff|Neg)\\) def. [^\\s]+ ([A-Za-z]+?) - ([A-Za-z]+?)( \\((.+?)\\))? \\((Aff|Neg)\\)");
								doc.getElementsByTag("font").unwrap();
								Matcher matcher = pattern.matcher(doc.toString().replaceAll("<br>", ""));
								int count = 0;
								while(matcher.find())
									count += 2;
								System.out.println(count);
								if(tournamentExists(doc.baseUri(), count, sql))
									log.log(JOT, t.getName() + " double octos is up to date.");
								else {
									
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM pf_rounds WHERE absUrl=?", doc.baseUri());
									
									matcher.reset();
									ArrayList<Object> args = new ArrayList<Object>();
									String query = "INSERT INTO pf_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ";
									while(matcher.find()) {
										// First debater
										ArrayList<Object> a = new ArrayList<Object>();
										a.add(t.getLink());
										a.add(doc.baseUri());
										
										Debater leftDebater = getDebaterFromLastName(sql, matcher.group(1), matcher.group(4));
										Debater rightDebater = getDebaterFromLastName(sql, matcher.group(2), matcher.group(4));
										Debater leftAgainst = getDebaterFromLastName(sql, matcher.group(6), matcher.group(9));
										Debater rightAgainst = getDebaterFromLastName(sql, matcher.group(7), matcher.group(9));
										if(leftDebater == null || rightDebater == null || leftAgainst == null || rightAgainst == null)
											continue;
										Team team = new Team(leftDebater, rightDebater);
										team.setID(getTeamID(sql, team, "PF"));
										Team against = new Team(leftAgainst, rightAgainst);
										against.setID(getTeamID(sql, against, "PF"));
										
										a.add(team.getID());
										a.add(against.getID());
										a.add(Round.DOUBLE_OCTOS);
										a.add(matcher.group(5).equals("Aff") ? new Character('A') : new Character('N'));
										a.add("1-0");
										if(!overwrite) {
											ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM pf_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
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
										a.add(against.getID());
										a.add(team.getID());
										a.add(Round.DOUBLE_OCTOS);
										a.add(matcher.group(10).equals("Aff") ? new Character('A') : new Character('N'));
										a.add("0-1");
										if(!overwrite) {
											ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM pf_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6), a.get(7));
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
									if(!query.equals("INSERT INTO pf_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ")) {
										query = query.substring(0, query.lastIndexOf(", "));
										sql.executePreparedStatement(query, args.toArray());
										log.log(JOT, t.getName() + " double octos updated.");
									}
									else {
										log.log(JOT, t.getName() + " double octos is up to date.");
									}
								}
							}
							
							//Bracket
							Element bracket = eventRow.select("a[href]:contains(Bracket)").first();
							if(bracket != null) {
								Document doc = JsoupHelper.retryIfTimeout(bracket.absUrl("href"), 3);
								
								// If we have the same amount of entries, then do not check
								if(tournamentExists(doc.baseUri(), doc.select("table[cellspacing=0] > tbody > tr > td.botr, table[cellspacing=0] > tbody > tr > td.topr, table[cellspacing=0] > tbody > tr > td.top, table[cellspacing=0] > tbody > tr > td.btm").size() - 1, sql))
									log.log(JOT, t.getName() + " bracket is up to date.");
								else {
									// Overwrite
									if(overwrite)
										sql.executePreparedStatementArgs("DELETE FROM pf_rounds WHERE absUrl=?", doc.baseUri());
									
									// Parse rounds
									ArrayList<Object> args = new ArrayList<Object>();
									String query = "INSERT INTO pf_rounds (tournament, absUrl, team, against, round, decision) VALUES ";
									Round round = null, last = null;
									ArrayList<Pair<Team, Team>> matchup = new ArrayList<Pair<Team, Team>>();
									for(int i = 0;(round = getBracketRound(doc, i)) != null;i++) {

										ArrayList<Pair<Team, Team>> currentMatchup = new ArrayList<Pair<Team, Team>>();

										if(last == Round.FINALS) {
											Element element = doc.select("table[cellspacing=0] > tbody > tr > td.top:eq(" + i + ")").first();
											Element team = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
											String[] names = team.text().substring(team.text().indexOf(' ') + 1).split(" - ");
											if(names.length != 2)
												continue;
											currentMatchup.add(Pair.of(new Team(new Debater(null, null, names[0], null, null), new Debater(null, null, names[1], null, null)), null));
										}
										else {
											// Add all debaters to an arraylist of pairs
											Elements col = doc.select("table[cellspacing=0] > tbody > tr > td:eq(" + i + ")");
											Element left = null;
											for(Element element : col) {
												Element team = null;
												if (element.hasClass("btm") || element.hasClass("botr"))
													team = element;
												else if (element.hasClass("top") || element.hasClass("topr"))
													team = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
												else
													continue;
												if (left == null)
													left = team;
												else {
													String leftSchool = null,
															rightSchool = null,
															leftText = left.childNode(0).toString(),
															rightText = team.childNode(0).toString();
													if (left.childNodeSize() > 2)
														if (left.childNode(2) instanceof TextNode)
															leftSchool = left.childNode(2).toString();
														else
															leftSchool = left.childNode(2).unwrap().toString();
													if (team.childNodeSize() > 2)
														if (team.childNode(2) instanceof TextNode)
															rightSchool = team.childNode(2).toString();
														else
															rightSchool = team.childNode(2).unwrap().toString();
													String[] leftNames = leftText.substring(leftText.indexOf(' ') + 1).split(" - ");
													String[] rightNames = rightText.substring(rightText.indexOf(' ') + 1).split(" - ");
													if(leftNames.length != 2 || rightNames.length != 2)
														continue;
													Team l;
													if(leftText.contains("&nbsp;"))
														l = null;
													else
														l = new Team(new Debater(null, null, leftNames[0], null, leftSchool), new Debater(null, null, leftNames[1], null, leftSchool));
													Team r;
													if(rightText.contains("&nbsp;"))
														r = null;
													else
														r = new Team(new Debater(null, null, rightNames[0], null, rightSchool), new Debater(null, null, rightNames[1], null, rightSchool));
													currentMatchup.add(Pair.of(l, r));
													left = null;
												}
											}
										}

										if(matchup != null && last != null) {
											
											// Sort matchups into winner/loser pairs
											ArrayList<Pair<Team, Team>> winnerLoser = new ArrayList<Pair<Team, Team>>();
											for(Pair<Team, Team> winners : currentMatchup)
												for(Pair<Team, Team> matchups : matchup)
													if(winners != null)
														if((winners.getLeft() != null && matchups.getLeft() != null && winners.getLeft().equalsByLast(matchups.getLeft())) || (winners.getRight() != null && matchups.getRight() != null && winners.getRight().equalsByLast(matchups.getLeft())))
															winnerLoser.add(matchups);
														else if((winners.getLeft() != null && matchups.getRight() != null && winners.getLeft().equalsByLast(matchups.getRight())) || (winners.getRight() != null && matchups.getRight() != null && winners.getRight().equalsByLast(matchups.getRight())))
															winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));
											
											for(Pair<Team, Team> pair : winnerLoser) {
												
												// Winner
												
												ArrayList<Object> a = new ArrayList<Object>();
												a.add(t.getLink());
												a.add(doc.baseUri());
												a.add(pair.getLeft().getID());
												a.add(pair.getRight().getID());
												a.add(last.toString());
												a.add("1-0");
												
												if(!overwrite) {
													ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM pf_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND team=? AND against=? AND round<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5));
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
													ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM pf_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND team=? AND against=? AND round<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5));
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
											for(Pair<Team, Team> pair : currentMatchup) {
												pair.getLeft().setID(getTeamID(sql, pair.getLeft(), "PF"));
												pair.getRight().setID(getTeamID(sql, pair.getRight(), "PF"));
											}
										}
										
										if(last == Round.FINALS) {
											System.out.println(args);
											System.exit(0);
											if(!query.equals("INSERT INTO pf_rounds (tournament, absUrl, team, against, round, decision) VALUES ")) {
												query = query.substring(0, query.lastIndexOf(", "));
												sql.executePreparedStatement(query, args.toArray());
												log.log(JOT, t.getName() + " bracket updated.");
											}
											else {
												log.log(JOT, t.getName() + " bracket is up to date.");
											}
											break;
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
}
