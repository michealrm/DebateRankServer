package net.debaterank.server.modules.jot;

import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.DRHelper;
import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.EntryInfo;
import net.debaterank.server.util.JOTHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LD implements Runnable {

	private Logger log;
	private ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tournaments;
	private WorkerPool manager;

	public LD(ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tournaments, WorkerPool manager) {
		log = LogManager.getLogger(LD.class);
		this.tournaments = tournaments;
		this.manager = manager;
	}

	public void run() {
		// Scrape events per tournament
		for(EntryInfo tInfo : tournaments) {
			Tournament t = tInfo.getTournament();
			if(t.isLdScraped() || tInfo.getLdEventRows().isEmpty()) continue;
			manager.newModule(() -> {
				Session session = HibernateUtil.getSession();
				try {
					Transaction transaction = session.beginTransaction();
					ArrayList<LDBallot> ballots = new ArrayList<>();
					ArrayList<LDRound> tournRounds = new ArrayList<>();
					ArrayList<EntryInfo.JOTEventLinks> eventRows = tInfo.getLdEventRows();
					ArrayList<Debater> competitorsList = new ArrayList<>();
					log.info("Updating " + t.getName() + " " + t.getLink());
					for(EntryInfo.JOTEventLinks eventRow : eventRows) {
						// Prelims
						if(eventRow.prelims != null) {
							Document p = null;
							try {
								p = Jsoup.connect(eventRow.prelims).timeout(10 * 1000).get();
							} catch(UnsupportedMimeTypeException umte) {
								log.warn("UnsupportedMimeTypeException on " + t.getLink() + " prelims. Skipping this event row.");
								continue;
							}
							Element table = p.select("table[border=1]").first();
							Elements rows = table.select("tr:has(table)");

							// Register all debaters
							HashMap<String, Debater> competitors = new HashMap<>();
							for(Element row : rows) { // find debaters in the collection matching the first and last name and then perform the equals comparison
								Elements infos = row.select("td").first().select("td");
								Debater debater = new Debater(infos.get(3).text(), infos.get(1).text());
								debater = Debater.getDebaterOrInsert(debater);
								competitors.put(infos.get(2).text(), debater);
								competitorsList.add(debater);
							}

							// Parse rounds
							ArrayList<LDRound> rounds = new ArrayList<>();
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

									LDRound round = new LDRound(t);
									Debater againstDebater = null;
									if(win == null || win.text() == null || against == null) {
										continue;
									}
									if(win.text().equals("B") || win.text().equals("F")) {
										// bye
										if(win.text().equals("B")) {
											round.setA(debater);
											round.setN(debater);
										} else {
											LDBallot ballot = new LDBallot(round);
											if(side == null || side.text() == null || side.text().equals("Aff")) {
												round.setA(debater);
												ballot.setDecision("Neg");
												try {
													if(speaks.text() != null)
														ballot.setA_s(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
											}
											else {
												round.setN(debater);
												ballot.setDecision("Aff");
												try {
													if(speaks.text() != null)
														ballot.setN_s(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
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
										for(LDBallot ballot : ballots) {
											LDRound r = ballot.getRound();
											if(r.getA() != null && r.getA().getId().equals(debater.getId()) && r.getRound().equals(String.valueOf(k+1))) {
												r.setA(debater);
												try {
													if(speaks.text() != null)
														ballot.setA_s(Double.parseDouble(speaks.text().replaceAll("\\\\*", "")));
												} catch(NumberFormatException nfe) {}
												continue round;
											} else if(r.getN() != null && r.getN().getId().equals(debater.getId()) && r.getRound().equals(String.valueOf(k+1))) {
												r.setN(debater);
												try {
													if(speaks.text() != null)
														ballot.setN_s(Double.parseDouble(speaks.text()));
												} catch(NumberFormatException nfe) {}
												continue round;
											}
										}
										// no existing document found. we need to make a new one
										if ((win.text().equals("W") || win.text().equals("L")) && against.text() != null && (againstDebater = competitors.get(against.text())) != null) {
											LDBallot ballot = new LDBallot(round);
											if (side.text().equals("Aff")) {
												round.setA(debater);
												round.setN(againstDebater);
												ballot.setDecision(win.text().equals("W") ? "Aff" : "Neg");
												try {
													if (speaks.text() != null)
														ballot.setA_s(Double.parseDouble(speaks.text()));
												} catch (NumberFormatException nfe) {}
											} else { // neg
												round.setA(againstDebater);
												round.setN(debater);
												ballot.setDecision(win.text().equals("W") ? "Neg" : "Aff");
												try {
													if (speaks.text() != null)
														ballot.setN_s(Double.parseDouble(speaks.text()));
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
						if(eventRow.doubleOctas != null) {
							Document doc = Jsoup.connect(eventRow.doubleOctas).timeout(10*1000).get();

							Pattern pattern = Pattern.compile("[^\\s]+ (.+?)( \\((.+?)\\))? \\((Aff|Neg)\\) def. [^\\s]+ (.+?)( \\((.+?)\\))? \\((Aff|Neg)\\)");
							doc.getElementsByTag("font").unwrap();
							Matcher matcher = pattern.matcher(doc.toString().replaceAll("<br>", ""));
							ArrayList<LDRound> rounds = new ArrayList<>();
							while(matcher.find()) {
								LDRound round = new LDRound(t);
								round.setAbsUrl(doc.baseUri());
								Debater winner = new Debater(matcher.group(1), matcher.group(3));
								Debater loser = new Debater(matcher.group(5), matcher.group(7));
								winner = DRHelper.findDebater(competitorsList, winner);
								loser = DRHelper.findDebater(competitorsList, loser);

								if(matcher.group(4).equals("Aff")) {
									round.setA(winner);
									round.setN(loser);
								} else {
									round.setA(loser);
									round.setN(winner);
								}
								LDBallot ballot = new LDBallot(round);
								ballot.setDecision(matcher.group(4));
								ballots.add(ballot);
								round.setRound("DO");
								rounds.add(round);
							}
							tournRounds.addAll(rounds); // add results
						}

						//Bracket
						if(eventRow.bracket != null) {
							Document doc = null;
							try {
								doc = Jsoup.connect(eventRow.bracket).timeout(10*1000).get();
							} catch(UnsupportedMimeTypeException e) {
								log.info("Brackets tyep unsupported: " + e.getMimeType() + ". Skipping.");
								break;
							}

							// Parse rounds
							ArrayList<LDRound> rounds = new ArrayList<>();
							String roundStr, last = null;
							ArrayList<Pair<Debater, Debater>> matchup = new ArrayList<>();
							for(int i = 0; (roundStr = JOTHelper.getBracketRound(doc, i)) != null; i++) {

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
												try {
													left.childNode(0).toString();
													debater.childNode(0).toString();
												} catch(Exception e) {
													continue;
												}
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
												}
												Debater r;
												if (rightText.contains("&nbsp;"))
													r = null;
												else {
													r = new Debater(rightText.substring(rightText.indexOf(' ') + 1), rightSchool);
												}
												currentMatchup.add(Pair.of(l, r));
												left = null;
											}
										} catch(NullPointerException npe) {}
									}
								}

								if(matchup != null && last != null) {

									// Sort matchups into winner/loser pairs
									ArrayList<Pair<Debater, Debater>> winnerLoser = new ArrayList<>();
									for(Pair<Debater, Debater> winners : currentMatchup)
										for(Pair<Debater, Debater> matchups : matchup)
											if((winners.getLeft() != null && matchups.getLeft() != null && winners.getLeft().equals(matchups.getLeft())) || (winners.getRight() != null && matchups.getRight() != null && winners.getRight().equals(matchups.getLeft())))
												winnerLoser.add(matchups);
											else if((winners.getLeft() != null && matchups.getRight() != null && winners.getLeft().equals(matchups.getRight())) || (winners.getRight() != null && matchups.getRight() != null && winners.getRight().equals(matchups.getRight())))
												winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));

									for(Pair<Debater, Debater> pair : winnerLoser) {

										if(pair.getLeft() == null || pair.getRight() == null)
											continue;

										LDRound round = new LDRound(t);
										round.setAbsUrl(doc.baseUri());
										round.setA(Debater.getDebater(pair.getLeft()));
										round.setN(Debater.getDebater(pair.getRight()));
										round.setRound(last);
										LDBallot ballot = new LDBallot(round);
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
					for(LDBallot b : ballots)
						session.persist(b);
					t.setLdScraped(true);
					session.merge(t);
					transaction.commit();
					log.info("Updated " + t.getName());

				} catch(Exception e) {
					log.error(e);
					e.printStackTrace();
					log.fatal("Could not update " + t.getName());
				} finally {
					session.close();
				}
			});
		}
	}

}