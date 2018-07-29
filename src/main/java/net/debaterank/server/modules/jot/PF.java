package net.debaterank.server.modules.jot;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import net.debaterank.server.models.*;
import net.debaterank.server.modules.Module;
import net.debaterank.server.modules.WorkerPool;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.mongodb.morphia.Datastore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.debaterank.server.util.DRHelper.getBracketRound;

public class PF extends Module {

	private ArrayList<JOTEntryInfo> tournaments;
	private WorkerPool manager;
	private MongoCollection<Team> teamCollection;
	private MongoCollection<Debater> debaterCollection;
	private MongoCollection<School> schoolCollection;

	public PF(ArrayList<JOTEntryInfo> tournaments, WorkerPool manager, Datastore datastore, MongoDatabase db) {
		super(LogManager.getLogger(PF.class), datastore, db);
		debaterCollection = db.getCollection("debaters", Debater.class);
		schoolCollection = db.getCollection("schools", School.class);
		teamCollection = db.getCollection("teams", Team.class);
		this.tournaments = tournaments;
		this.manager = manager;
	}

	public void run() {

		// Scrape events per tournament
		for(JOTEntryInfo tInfo : tournaments) {
			Tournament t = tInfo.getTournament();
			manager.newModule(() -> {
				try {
					Elements eventRows = tInfo.getEventRows();
					log.info("Updating " + t.getName() + " " + t.getLink());
					ArrayList<Ballot> ballots = new ArrayList<>();
					ArrayList<Round> tournRounds = new ArrayList<>();
					for(Element eventRow : eventRows) {
						// Prelims
						Element prelim = eventRow.select("a[title]:contains(Prelims)").first();
						if(prelim != null) {
							Document p = Jsoup.connect(prelim.absUrl("href")).timeout(10*1000).get();
							Element table = p.select("table[border=1]").first();
							Elements rows = table.select("tr:has(table)");

							// Register all debaters
							HashMap<String, Team> competitors = new HashMap<String, Team>();
							for(Element row : rows) {
								Element info = row.select("td").first();
								String left = info.select("tr:eq(0)").text();
								String second = info.select("tr:eq(1)").text().replaceAll("\u00a0|&nbsp", " ");
								String key = second.split(" ")[0];
								String school = second.split(" ").length > 1 ? (second.substring(second.indexOf(' ') + 1)).trim() : null;
								String right = info.select("tr:eq(2)").text();
								Team team = new Team(new Debater(left, school), new Debater(right, school));
								team.updateToDocument(datastore, teamCollection, debaterCollection, schoolCollection);
								competitors.put(key, team);
							}

							// Parse rounds
							ArrayList<Round> rounds = new ArrayList<>();
							for(int i = 0;i<rows.size();i++) {
								String key = rows.get(i).select("td").first().select("tr:eq(1)").text().replaceAll("\u00a0|&nbsp", " ").split(" ")[0];
								Team team = competitors.get(key);
								if(team == null) {
									log.warn("Couldn't find " + key + " in the competitors hashmap. " + t.getLink());
									continue;
								}
								Elements cols = rows.get(i).select("td[width=80]");
								round:
								for(int k = 0;k<cols.size();k++) {
									Element top, win, side, against, bot, speaks1, speaks2, place1, place2;
									try {
										top = cols.get(k).select("tr:eq(0)").first();
										speaks1 = top.select("td[align=left]").first();
										against = top.select("td[align=center]").first();
										place1 = top.select("td[align=right]").first();

										win = cols.get(k).select(".rec").first();

										bot = cols.get(k).select("tr:eq(2)").first();
										speaks2 = bot.select("td[align=left]").first();
										side = bot.select("td[align=center]").first();
										place2 = bot.select("td[align=right]").first();

									}
									catch(Exception e) {
										continue;
									}
									Round round = new Round(t);
									Team againstTeam = null;
									if(win == null || win.text() == null || against == null) {
										continue;
									}

									if(win.text().equals("B") || win.text().equals("F")) {
										Ballot ballot = new Ballot(round);
										// bye
										if(win.text().equals("B")) {
											round.setTeamAff(team);
											round.setTeamNeg(team);
										} else {
											if(side == null || side.text() == null || side.text().equals("Pro") || side.text().equals("Aff")) {
												round.setTeamAff(team);
												ballot.setDecision("Neg");
												try {
													if(speaks1.text() != null)
														ballot.setAff1_speaks(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
													if(speaks2.text() != null)
														ballot.setAff2_speaks(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												try {
													if(place1.text() != null)
														ballot.setAff1_place(Integer.parseInt(place1.text()));
													if(place2.text() != null)
														ballot.setAff2_place(Integer.parseInt(place2.text()));
												} catch(NumberFormatException nfe) {}
											}
											else {
												round.setTeamNeg(team);
												ballot.setDecision("Aff");
												try {
													if(speaks1.text() != null)
														ballot.setNeg1_speaks(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
													if(speaks2.text() != null)
														ballot.setNeg2_speaks(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												try {
													if(place1.text() != null)
														ballot.setNeg1_place(Integer.parseInt(place1.text()));
													if(place2.text() != null)
														ballot.setNeg2_place(Integer.parseInt(place2.text()));
												} catch(NumberFormatException nfe) {}
											}
										}
										ballots.add(ballot);
										round.setBye(true);
										round.setRound(String.valueOf(k+1));
										round.setAbsUrl(p.baseUri());
										rounds.add(round);
									} else if(win.text() != null && side != null && side.text() != null && (side.text().equals("Pro") || side.text().equals("Con") || side.text().equals("Aff") || side.text().equals("Neg"))) {
										// check if other side (aff / neg) is competitors.get(against.text()) win.text().equals("F")
										for(Ballot ballot : ballots) {
											Round r = ballot.getRound();
											if(r.getTeamAff() != null && r.getTeamAff().equals(team) && r.getRound().equals(String.valueOf(k+1))) {
												try {
													if(speaks1.text() != null)
														ballot.setAff1_speaks(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
													if(speaks2.text() != null)
														ballot.setAff2_speaks(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												try {
													if(place1.text() != null)
														ballot.setAff1_place(Integer.parseInt(place1.text()));
													if(place2.text() != null)
														ballot.setAff2_place(Integer.parseInt(place2.text()));
												} catch(NumberFormatException nfe) {}
												continue round;
											} else if(r.getTeamNeg() != null && r.getTeamNeg().equals(team) && r.getRound().equals(String.valueOf(k+1))) {
												try {
													if(speaks1.text() != null)
														ballot.setNeg1_speaks(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
													if(speaks2.text() != null)
														ballot.setNeg2_speaks(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												try {
													if(place1.text() != null)
														ballot.setNeg1_place(Integer.parseInt(place1.text()));
													if(place2.text() != null)
														ballot.setNeg2_place(Integer.parseInt(place2.text()));
												} catch(NumberFormatException nfe) {}
												continue round;
											}
										}
										// no existing document found. we need to make a new one
										if ((win.text().equals("W") || win.text().equals("L")) && against.text() != null && (againstTeam = competitors.get(against.text())) != null) {
											Ballot ballot = new Ballot(round);
											if (side.text().equals("Pro") || side.text().equals("Aff")) {
												round.setTeamAff(team);
												round.setTeamNeg(againstTeam);
												ballot.setDecision(win.text().equals("W") ? "Aff" : "Neg");
												try {
													if(speaks1.text() != null)
														ballot.setAff1_speaks(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
													if(speaks2.text() != null)
														ballot.setAff2_speaks(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												try {
													if(place1.text() != null)
														ballot.setAff1_place(Integer.parseInt(place1.text()));
													if(place2.text() != null)
														ballot.setAff2_place(Integer.parseInt(place2.text()));
												} catch(NumberFormatException nfe) {}
											} else { // neg
												round.setTeamAff(againstTeam);
												round.setTeamNeg(team);
												ballot.setDecision(win.text().equals("W") ? "Neg" : "Aff");
												try {
													if(speaks1.text() != null)
														ballot.setNeg1_speaks(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
													if(speaks2.text() != null)
														ballot.setNeg2_speaks(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												try {
													if(place1.text() != null)
														ballot.setNeg1_place(Integer.parseInt(place1.text()));
													if(place2.text() != null)
														ballot.setNeg2_place(Integer.parseInt(place2.text()));
												} catch(NumberFormatException nfe) {}
											}
											ballots.add(ballot);
											round.setRound(String.valueOf(k + 1));
											round.setAbsUrl(p.baseUri());
											rounds.add(round);
										}
									} else {
										log.warn("Not logging a round. " + key + " " + (win == null ? null : win.text()) + " " + p.baseUri());
									}
								}
							}
                            tournRounds.addAll(rounds); // add results
                        }
							
						// Double Octos
						Element doubleOctos = eventRow.select("a[href]:contains(Double Octos)").first();
						if(doubleOctos != null) {
							Document doc = Jsoup.connect(doubleOctos.absUrl("href")).timeout(10*1000).get();

							Pattern pattern = Pattern.compile("[^\\s]+ ([A-Za-z]+?) - ([A-Za-z]+?)( \\((.+?)\\))? \\((Aff|Neg)\\) def. [^\\s]+ ([A-Za-z]+?) - ([A-Za-z]+?)( \\((.+?)\\))? \\((Aff|Neg)\\)");
							doc.getElementsByTag("font").unwrap();
							Matcher matcher = pattern.matcher(doc.toString().replaceAll("<br>", ""));
							ArrayList<Round> rounds = new ArrayList<>();
							while(matcher.find()) {

								String leftDebater = matcher.group(1);
								String rightDebater = matcher.group(2);
								String debaterSchoolString = matcher.group(4);
								String leftAgainst = matcher.group(6);
								String rightAgainst = matcher.group(7);
								String againstSchoolString = matcher.group(9);

								if(debaterSchoolString == null || againstSchoolString == null || leftDebater == null || rightDebater == null || leftAgainst == null || rightAgainst == null) {
									log.warn("null in DO! Skipping round " + t.getLink());
									continue;
								}

								School debaterSchool = new School(debaterSchoolString);
								debaterSchool.updateToDocument(datastore, schoolCollection);
								School againstSchool = new School(againstSchoolString);
								againstSchool.updateToDocument(datastore, schoolCollection);

								if(debaterSchool == null || againstSchool == null) {
									log.warn("School null in DO! Skipping round " + t.getLink());
									continue;
								}

								Team team = Team.getTeamFromLastName(leftDebater, rightDebater, debaterSchool, debaterCollection);
								Team against = Team.getTeamFromLastName(leftAgainst, rightAgainst, againstSchool, debaterCollection);

								if(team == null || against == null) {
									log.warn("Team null in DO! " + Arrays.asList(leftDebater, rightDebater, leftAgainst, rightAgainst, debaterSchoolString, againstSchoolString)+ "Skipping round " + t.getLink());
									continue;
								}

								Round round = new Round(t);
								round.setAbsUrl(doc.baseUri());
								if(matcher.group(5).equals("Pro") || matcher.group(5).equals("Aff")) {
									round.setTeamAff(team);
									round.setTeamNeg(against);
								} else {
									round.setTeamAff(against);
									round.setTeamNeg(team);
								}
								Ballot ballot = new Ballot(round);
								ballot.setDecision(matcher.group(5));
								round.setRound("DO");
								rounds.add(round);
							}
							tournRounds.addAll(rounds); // add results
						}
						
						//Bracket
						Element bracket = eventRow.select("a[href]:contains(Bracket)").first();
						if(bracket != null) {
							Document doc = Jsoup.connect(bracket.absUrl("href")).timeout(10*1000).get();

							// Parse rounds
							ArrayList<Round> rounds = new ArrayList<>();
							String roundStr = null, last = null;
							ArrayList<Pair<Team, Team>> matchup = new ArrayList<Pair<Team, Team>>();
							for(int i = 0;(roundStr = getBracketRound(doc, i)) != null;i++) {

								ArrayList<Pair<Team, Team>> currentMatchup = new ArrayList<Pair<Team, Team>>();

								if(last != null && last.equals("F")) {
									Element element = doc.select("table[cellspacing=0] > tbody > tr > td.top:eq(" + i + ")").first();
									Element team = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
									String[] names = team.text().substring(team.text().indexOf(' ') + 1).split(" - ");
									if(names.length != 2)
										continue;
									currentMatchup.add(Pair.of(new Team(new Debater(null, null, names[0], null, null), new Debater(null, null, names[1], null, null)), (Team)null));
								}
								else {
									// Add all debaters to an arraylist of pairs
									Elements col = doc.select("table[cellspacing=0] > tbody > tr > td:eq(" + i + ")");
									Element left = null;
									for(Element element : col) {
										Element team = null;
										if(element.hasClass("btm") || element.hasClass("botr"))
											team = element;
										else if(element.hasClass("top") || element.hasClass("topr"))
											team = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
										else
											continue;
										if(left == null)
											left = team;
										else {
											String leftSchool = null,
													rightSchool = null,
													leftText = left.childNode(0).toString(),
													rightText = team.childNode(0).toString();
											if(left.childNodeSize() > 2)
												if(left.childNode(2) instanceof TextNode)
													leftSchool = left.childNode(2).toString();
												else
													leftSchool = left.childNode(2).unwrap().toString();
											if(team.childNodeSize() > 2)
												if(team.childNode(2) instanceof TextNode)
													rightSchool = team.childNode(2).toString();
												else
													rightSchool = team.childNode(2).unwrap().toString();
											String[] leftNames = leftText.substring(leftText.indexOf(' ') + 1).split(" - ");
											String[] rightNames = rightText.substring(rightText.indexOf(' ') + 1).split(" - ");
											if((leftNames.length != 2 && !leftText.contains("&nbsp;")) || (rightNames.length != 2 && !rightText.contains("&nbsp;")))
												continue;
											Team l;
											if(leftText.contains("&nbsp;"))
												l = null;
											else {
												School school = new School(leftSchool);
												school.updateToDocument(datastore, schoolCollection);
												l = Team.getTeamFromLastName(leftNames[0], leftNames[1], school, debaterCollection);
											}
											Team r;
											if(rightText.contains("&nbsp;"))
												r = null;
											else {
												School school = new School(rightSchool);
												school.updateToDocument(datastore, schoolCollection);
												r = Team.getTeamFromLastName(leftNames[0], leftNames[1], school, debaterCollection);
											}
											currentMatchup.add(Pair.of(l, r));
											left = null;
										}
									}
								}

								if(matchup != null && last != null) {

									// Sort matchups into winner/loser pairs
									ArrayList<Pair<Team, Team>> winnerLoser = new ArrayList<Pair<Team, Team>>();
									for(Pair<Team, Team> winners : currentMatchup)
										for(Pair<Team, Team> matchups : matchup) {
											if(matchups.getLeft() != null) {
												if(winners.getLeft() != null && winners.getLeft().equalsByLastName(matchups.getLeft()))
													winnerLoser.add(matchups);
												if(winners.getRight() != null && winners.getRight().equalsByLastName(matchups.getLeft()))
													winnerLoser.add(matchups);
											}
											if(matchups.getRight() != null) {
												if(winners.getLeft() != null && winners.getLeft().equalsByLastName(matchups.getRight()))
													winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));
												if(winners.getRight() != null && winners.getRight().equalsByLastName(matchups.getRight()))
													winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));
											}
										}

									for(Pair<Team, Team> pair : winnerLoser) {

										if(pair.getLeft() == null || pair.getRight() == null)
											continue;

										Round round = new Round(t);
										round.setAbsUrl(doc.baseUri());
										round.setTeamAff(pair.getLeft());
										round.setTeamNeg(pair.getRight());
										round.setNoSide(true);
										Ballot ballot = new Ballot(round);
										ballot.setDecision("Aff");
										ballots.add(ballot);

										rounds.add(round);
									}
								}

								last = roundStr;
								matchup = currentMatchup;
							}
							tournRounds.addAll(rounds); // add results
						}
					}
					datastore.save(tournRounds);
					datastore.save(ballots);
					log.info("Updated " + t.getName());
				} catch(Exception e) {
					log.error(e);
					e.printStackTrace();
					log.fatal("Could not update " + t.getName());
				}
			});
		}
	}
}
