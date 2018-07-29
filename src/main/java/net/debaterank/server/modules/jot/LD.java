package net.debaterank.server.modules.jot;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import net.debaterank.server.models.*;
import net.debaterank.server.modules.Module;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.DRHelper;
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

public class LD extends Module {
	
	private ArrayList<JOTEntryInfo> tournaments;
	private WorkerPool manager;
	private MongoCollection<Debater> debaterCollection;
	private MongoCollection<School> schoolCollection;

	public LD(ArrayList<JOTEntryInfo> tournaments, WorkerPool manager, Datastore datastore, MongoDatabase db) {
		super(LogManager.getLogger(LD.class), datastore, db);
		debaterCollection = db.getCollection("debaters", Debater.class);
		schoolCollection = db.getCollection("schools", School.class);
		this.tournaments = tournaments;
		this.manager = manager;
	}
	
	public void run() {
		// Scrape events per tournament
		for(JOTEntryInfo tInfo : tournaments) {
			Tournament t = tInfo.getTournament();
			manager.newModule(() -> {
				try {
					ArrayList<Ballot> ballots = new ArrayList<>();
					ArrayList<Round> tournRounds = new ArrayList<>();
					Elements eventRows = tInfo.getEventRows();
					log.info("Updating " + t.getName() + " " + t.getLink());
					for(Element eventRow : eventRows) {
						// Prelims
						Element prelim = eventRow.select("a[title]:contains(Prelims)").first();
						if(prelim != null) {
							Document p = Jsoup.connect(prelim.absUrl("href")).timeout(10*1000).get();
							Element table = p.select("table[border=1]").first();
							Elements rows = table.select("tr:has(table)");

							// Register all debaters
							HashMap<String, Debater> competitors = new HashMap<String, Debater>();
							for(Element row : rows) { // find debaters in the collection matching the first and last name and then perform the equals comparison
								Elements infos = row.select("td").first().select("td");
								Debater debater = new Debater(infos.get(3).text(), infos.get(1).text());
								debater.updateToDocument(datastore, debaterCollection, schoolCollection);
								competitors.put(infos.get(2).text(), debater);
							}

							// Parse rounds
							ArrayList<Round> rounds = new ArrayList<>();
							for(int i = 0;i<rows.size();i++) {
								String key = rows.get(i).select("td").first().select("td").get(2).text();
								Debater debater = competitors.get(key);
								if(debater == null) {
									log.warn("Couldn't find " + key + " in the competitors hashmap. " + t.getLink());
									continue;
								}
								Elements cols = rows.get(i).select("td[width=80]");
								round:
								for(int k = 0;k<cols.size();k++) {
									Element speaks = cols.get(k).select("[width=50%][align=left]").first();
									Element side = cols.get(k).select("[width=50%][align=right]").first();
									Element win = cols.get(k).select("[colspan=2].rec").first();
									Element against = cols.get(k).select("[colspan=2][align=right]").first();

									Round round = new Round(t);
									Debater againstDebater = null;
									if(win == null || win.text() == null || against == null) {
										continue;
									}
									if(win.text().equals("B") || win.text().equals("F")) {
										// bye
										if(win.text().equals("B")) {
											round.setSingleAff(debater);
											round.setSingleNeg(debater);
										} else {
											Ballot ballot = new Ballot(round);
											if(side == null || side.text() == null || side.text().equals("Aff")) {
												round.setSingleAff(debater);
												ballot.setDecision("Neg");
												try {
													if(speaks.text() != null)
														ballot.setAff1_speaks(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
											}
											else {
												round.setSingleNeg(debater);
												ballot.setDecision("Aff");
												try {
													if(speaks.text() != null)
														ballot.setNeg1_speaks(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
											}
                                            ballots.add(ballot);
                                        }
										round.setBye(true);
										round.setRound(String.valueOf(k+1));
										round.setAbsUrl(p.baseUri());
										rounds.add(round);
									} else if(win.text() != null && side != null && side.text() != null && (side.text().equals("Aff") || side.text().equals("Neg"))) {
										// check if other side (aff / neg) is competitors.get(against.text()) win.text().equals("F")
										for(Ballot ballot : ballots) {
										    Round r = ballot.getRound();
											if(r.getSingleAff() != null && r.getSingleAff().getId().equals(debater.getId()) && r.getRound().equals(String.valueOf(k+1))) {
												r.setSingleAff(debater);
												try {
													if(speaks.text() != null)
														ballot.setAff1_speaks(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												continue round;
											} else if(r.getSingleNeg() != null && r.getSingleNeg().getId().equals(debater.getId()) && r.getRound().equals(String.valueOf(k+1))) {
												r.setSingleNeg(debater);
												try {
													if(speaks.text() != null)
														ballot.setNeg1_speaks(Double.parseDouble(speaks.text()));
												} catch(NumberFormatException nfe) {}
												continue round;
											}
										}
										// no existing document found. we need to make a new one
										if ((win.text().equals("W") || win.text().equals("L")) && against.text() != null && (againstDebater = competitors.get(against.text())) != null) {
                                            Ballot ballot = new Ballot(round);
										    if (side.text().equals("Aff")) {
												round.setSingleAff(debater);
												round.setSingleNeg(againstDebater);
												ballot.setDecision(win.text().equals("W") ? "Aff" : "Neg");
												try {
													if (speaks.text() != null)
														ballot.setAff1_speaks(Double.parseDouble(speaks.text()));
												} catch (NumberFormatException nfe) {}
											} else { // neg
												round.setSingleAff(againstDebater);
												round.setSingleNeg(debater);
												ballot.setDecision(win.text().equals("W") ? "Neg" : "Aff");
												try {
													if (speaks.text() != null)
														ballot.setNeg1_speaks(Double.parseDouble(speaks.text()));
												} catch (NumberFormatException nfe) {}
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
						Element doubleOctos = eventRow.select("a[title]:contains(Double Octos)").first();
						if(doubleOctos != null) {
							Document doc = Jsoup.connect(doubleOctos.absUrl("href")).timeout(10*1000).get();

							Pattern pattern = Pattern.compile("[^\\s]+ (.+?)( \\((.+?)\\))? \\((Aff|Neg)\\) def. [^\\s]+ (.+?)( \\((.+?)\\))? \\((Aff|Neg)\\)");
							doc.getElementsByTag("font").unwrap();
							Matcher matcher = pattern.matcher(doc.toString().replaceAll("<br>", ""));
							ArrayList<Round> rounds = new ArrayList<>();
							while(matcher.find()) {
								Round round = new Round(t);
								round.setAbsUrl(doc.baseUri());
								Debater winner = new Debater(matcher.group(1), matcher.group(3));
								winner.updateToDocument(datastore, debaterCollection, schoolCollection);
								Debater loser = new Debater(matcher.group(5), matcher.group(7));
								loser.updateToDocument(datastore, debaterCollection, schoolCollection);

								if(matcher.group(4).equals("Aff")) {
									round.setSingleAff(winner);
									round.setSingleNeg(loser);
								} else {
									round.setSingleAff(loser);
									round.setSingleNeg(winner);
								}
								Ballot ballot = new Ballot(round);
								ballot.setDecision(matcher.group(4));
								round.setRound("DO");
								rounds.add(round);
							}
							tournRounds.addAll(rounds); // add results
						}

						//Bracket
						Element bracket = eventRow.select("a[title]:contains(Bracket)").first();
						if(bracket != null) {
							Document doc = Jsoup.connect(bracket.absUrl("href")).timeout(10*1000).get();

							// Parse rounds
							ArrayList<Round> rounds = new ArrayList<>();
							String roundStr, last = null;
							ArrayList<Pair<Debater, Debater>> matchup = new ArrayList<>();
							for(int i = 0;(roundStr = DRHelper.getBracketRound(doc, i)) != null;i++) {

								ArrayList<Pair<Debater, Debater>> currentMatchup = new ArrayList<>();

								if(last != null && last.equals("F")) {
									Element element = doc.select("table[cellspacing=0] > tbody > tr > td.top:eq(" + i + ")").first();
									Element debater = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
									currentMatchup.add(Pair.of(new Debater(debater.text().substring(debater.text().indexOf(' ') + 1), (String)null), (Debater)null));
								}
								else {
									// Add all debaters to an arraylist of pairs
									Elements col = doc.select("table[cellspacing=0] > tbody > tr > td:eq(" + i + ")");
									Element left = null;
									for(Element element : col) {
										try {
											Element debater = null;
											if (element.hasClass("btm") || element.hasClass("botr"))
												debater = element;
											else if (element.hasClass("top") || element.hasClass("topr"))
												debater = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
											else
												continue;
											if (left == null)
												left = debater;
											else {
												String leftSchool = null,
														rightSchool = null,
														leftText = left.childNode(0).toString(),
														rightText = debater.childNode(0).toString();
												if (left.childNodeSize() > 2)
													if (left.childNode(2) instanceof TextNode)
														leftSchool = left.childNode(2).toString();
													else
														leftSchool = left.childNode(2).unwrap().toString();
												if (debater.childNodeSize() > 2)
													if (debater.childNode(2) instanceof TextNode)
														rightSchool = debater.childNode(2).toString();
													else
														rightSchool = debater.childNode(2).unwrap().toString();
												Debater l;
												if (leftText.contains("&nbsp;"))
													l = null;
												else {
													l = new Debater(leftText.substring(leftText.indexOf(' ') + 1), leftSchool);
													l.updateToDocument(datastore, debaterCollection, schoolCollection);
												}
												Debater r;
												if (rightText.contains("&nbsp;"))
													r = null;
												else {
													r = new Debater(rightText.substring(rightText.indexOf(' ') + 1), rightSchool);
													r.updateToDocument(datastore, debaterCollection, schoolCollection);
												}
												currentMatchup.add(Pair.of(l, r));
												left = null;
											}
										} catch(NullPointerException npe) {}
									}
								}

								if(matchup != null && last != null) {

									// Sort matchups into winner/loser pairs
									ArrayList<Pair<Debater, Debater>> winnerLoser = new ArrayList<Pair<Debater, Debater>>();
									for(Pair<Debater, Debater> winners : currentMatchup)
										for(Pair<Debater, Debater> matchups : matchup)
											if((winners.getLeft() != null && matchups.getLeft() != null && winners.getLeft().equals(matchups.getLeft())) || (winners.getRight() != null && matchups.getRight() != null && winners.getRight().equals(matchups.getLeft())))
												winnerLoser.add(matchups);
											else if((winners.getLeft() != null && matchups.getRight() != null && winners.getLeft().equals(matchups.getRight())) || (winners.getRight() != null && matchups.getRight() != null && winners.getRight().equals(matchups.getRight())))
												winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));

									for(Pair<Debater, Debater> pair : winnerLoser) {

										if(pair.getLeft() == null || pair.getRight() == null)
											continue;

										Round round = new Round(t);
										round.setAbsUrl(doc.baseUri());
										round.setNoSide(true);
										round.setSingleAff(pair.getLeft());
										round.setSingleNeg(pair.getRight());
										round.setRound(last);
										Ballot ballot = new Ballot(round);
										ballot.setDecision("Aff");

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
