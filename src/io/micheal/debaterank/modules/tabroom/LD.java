package io.micheal.debaterank.modules.tabroom;

import static io.micheal.debaterank.util.DebateHelper.TABROOM;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.Judge;
import io.micheal.debaterank.Tournament;
import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.SQLHelper;

// XML Parsing sucks.
public class LD extends Module {

	private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	private final boolean overwrite;
	private SAXParserFactory factory;
	private ArrayList<Runnable> getTournamentRoundEntries;
	private ArrayList<Runnable> enterTournament;

	public LD(SQLHelper sql, Logger log, ArrayList<Tournament> tournaments, WorkerPool manager) {
		super(sql, log);
		this.tournaments = tournaments;
		this.manager = manager;
		getTournamentRoundEntries = new ArrayList<>();
		enterTournament = new ArrayList<>();
		
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
		for(Tournament t : tournaments) {
			manager.newModule(new Runnable() {
				public void run() {
					try {

						String tournIDStr = "";
						int index = t.getLink().indexOf("tourn_id=") + 8;
						while(index < t.getLink().length()) {
							index++;
							try {
								Integer.parseInt(Character.toString(t.getLink().toCharArray()[index]));
							}
							catch(Exception e) {
								break;
							}
							tournIDStr += Character.toString(t.getLink().toCharArray()[index]);
						}

						int tourn_id = Integer.parseInt(tournIDStr);

						String[] split = t.getDate().split("-| ");
						URL url = new URL("https://www.tabroom.com/api/current_tournaments.mhtml?timestring=" + split[0] + "-" + split[1] + "-" + split[2] + "T12:00:00");

						factory = SAXParserFactory.newInstance();
						factory.setFeature("http://xml.org/sax/features/namespaces", false);
						factory.setFeature("http://xml.org/sax/features/validation", false);
						SAXParser saxParser = factory.newSAXParser();

						DefaultHandler handler = new DefaultHandler() {

							private boolean bevent, beventname, bid, btourn;
							private String eventname;
							private int event_id, tid;

							public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
								if(qName.equalsIgnoreCase("EVENT")) {
									bevent = true;
									eventname = null;
									event_id = 0;
									tid = 0;
								}
								if(qName.equalsIgnoreCase("EVENTNAME") && bevent)
									beventname = true;
								if(qName.equalsIgnoreCase("ID") && bevent)
									bid = true;
								if(qName.equalsIgnoreCase("TOURN") && bevent)
									btourn = true;
							}

							public void endElement(String uri, String localName, String qName) throws SAXException {
								if(qName.equalsIgnoreCase("EVENT")) {
									bevent = false;
									eventname = null;
									event_id = 0;
									tid = 0;
								}
								if(qName.equalsIgnoreCase("EVENTNAME") && bevent)
									beventname = false;
								if(qName.equalsIgnoreCase("ID") && bevent)
									bid = false;
								if(qName.equalsIgnoreCase("TOURN") && bevent)
									btourn = false;
							}

							public void characters(char ch[], int start, int length) throws SAXException {
								if(beventname) {
									beventname = false;
									eventname = new String(ch, start, length);
								}
								if(bid) {
									bid = false;
									event_id = Integer.parseInt(new String(ch, start, length));
								}
								if(btourn) {
									btourn = false;
									tid = Integer.parseInt(new String(ch, start, length));
								}
								if(eventname != null && event_id != 0 && tid != 0 && bevent) {
									bevent = false;
									if (eventname.matches("^.*(LD|Lincoln|L-D).*$") && tourn_id == tid) {

										try {
												log.log(TABROOM, "Queuing " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event_id);
//												getTournamentRoundEntries.add(new Runnable() {
//													
//													@Override
//													public void run() {
//														//If we have the same amount of entries, then do not check
//														if (tournamentExists(t.getLink() + "|" + event_id, getTournamentRoundEntries(tourn_id, event_id), sql, "ld_rounds")) {
//															log.log(TABROOM, t.getName() + " prelims is up to date.");
//															return;
//														}
//												
//													}
//												});
												
												enterTournament.add(new Runnable() {
													
													@Override
													public void run() {
														try {
															enterTournament(t, tourn_id, event_id);
														} catch(Exception e) {
															log.log(TABROOM, "Could not update " + t.getName());
															e.printStackTrace();
														}
													}
												});

										} catch(Exception e) {e.printStackTrace();}
									}
								}
							}
						};

						saxParser.parse(url.openStream(), handler);

					} catch(IOException ioe) {ioe.printStackTrace();}
					catch (SAXException e) { e.printStackTrace();
					} catch (ParserConfigurationException e) { e.printStackTrace();
					}
				}
			});
		}

		boolean running = true; // TODO: Remove this
		while(running) {
			try {
				Thread.sleep(5000);
			} catch(InterruptedException e) {
				return;
			}
			running = manager.getActiveCount() != 0;
		}
		
		System.out.println("Done");
		
		// getTournamentRoundEntries
		
		for(Runnable runnable : enterTournament) {
			manager.newModule(runnable);;
		}

	}

	private ThreadLocal<Integer> size = new ThreadLocal<Integer>(); // for getTournamentRoundEntries

	private int getTournamentRoundEntries(int tourn_id, int event_id) throws XMLStreamException, IOException, SAXException, ParserConfigurationException {
		size.set(0);
		URL url = new URL("https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id);
		InputStream stream = url.openStream();

		SAXParser saxParser = factory.newSAXParser();
		DefaultHandler resultHandler = new DefaultHandler() {

			private boolean bballot_score, bscore_id;
			private String score_id;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = true;
					score_id = null;
				}
				if(qName.equalsIgnoreCase("SCORE_ID") && bballot_score)
					bscore_id = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = false;
					score_id = null;
				}
				if(qName.equalsIgnoreCase("SCORE_ID") && bballot_score)
					bscore_id = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bscore_id) {
					bscore_id = false;
					score_id = new String(ch, start, length);
				}
				if(score_id != null && bballot_score) {
					bballot_score = false;
					if(score_id.equals("WIN")) {
						size.set(size.get() + 1);
					}
				}
			}
		};

		saxParser.parse(stream, resultHandler);
		int localSize = size.get().intValue();

		return localSize;
	}

	private void enterTournament(Tournament t, int tourn_id, int event_id) throws ParserConfigurationException,IOException, SAXException, XMLStreamException {
		
		if (getTournamentRoundEntries(tourn_id, event_id) == 0)
			return;
		
		log.log(TABROOM, "Updating " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event_id);
		
		//Overwrite
		try {
			if (overwrite) {
				sql.executePreparedStatementArgs("DELETE FROM ld_judges WHERE round IN (SELECT id FROM ld_rounds WHERE absUrl=?)", t.getLink() + "|" + event_id);
				sql.executeStatement("SET FOREIGN_KEY_CHECKS=0");
				sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", t.getLink() + "|" + event_id);
				sql.executeStatement("SET FOREIGN_KEY_CHECKS=1");
			}
		} catch(SQLException sqle) {
			sqle.printStackTrace();
			log.error(sqle);
			log.fatal("Could not update " + t.getName() + " - " + sqle.getErrorCode());
			return;
		}

		SAXParser saxParser = factory.newSAXParser();
		URL url = new URL("https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id);

		InputStream iStream = url.openStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int len;
		while ((len = iStream.read(buffer)) > -1 ) {
			baos.write(buffer, 0, len);
		}
		baos.flush();
		iStream.close();
		InputStream stream = new ByteArrayInputStream(baos.toByteArray());

		// Getting schools
		HashMap<Integer, String> schools = new HashMap<Integer, String>();
		DefaultHandler schoolHandler = new DefaultHandler() {

			private boolean bschool, bid, bschoolname;
			private String schoolname;
			private int id;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("SCHOOL")) {
					bschool = true;
					schoolname = null;
					id = 0;
				}
				if(qName.equalsIgnoreCase("ID") && bschool)
					bid = true;
				if(qName.equalsIgnoreCase("SCHOOLNAME") && bschool)
					bschoolname = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("SCHOOL")) {
					bschool = false;
					schoolname = null;
					id = 0;
				}
				if(qName.equalsIgnoreCase("ID") && bschool)
					bid = false;
				if(qName.equalsIgnoreCase("SCHOOLNAME") && bschool)
					bschoolname = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if (bschoolname) {
					bschoolname = false;
					schoolname = new String(ch, start, length);
				}
				if(id != 0 && schoolname != null && bschool) {
					bschool = false;
					schools.put(id, schoolname);
				}
			}
		};

		saxParser.parse(stream, schoolHandler);

		// Getting competitors
		HashMap<Integer, Debater> competitors = new HashMap<Integer, Debater>();
		DefaultHandler competitorHandler = new DefaultHandler() {

			private boolean bentry, bid, bschool, bfullname;
			private String fullname;
			private int id, school;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("ENTRY")) {
					bentry = true;
					fullname = null;
					id = 0;
					school = 0;
				}
				if(qName.equalsIgnoreCase("FULLNAME") && bentry)
					bfullname = true;
				if(qName.equalsIgnoreCase("SCHOOL") && bentry)
					bschool = true;
				if(qName.equalsIgnoreCase("ID") && bentry)
					bid = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("ENTRY")) {
					bentry = false;
					fullname = null;
					id = 0;
					school = 0;
				}
				if(qName.equalsIgnoreCase("FULLNAME") && bentry)
					bfullname = false;
				if(qName.equalsIgnoreCase("SCHOOL") && bentry)
					bschool = false;
				if(qName.equalsIgnoreCase("ID") && bentry)
					bid = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bfullname) {
					bfullname = false;
					fullname = new String(ch, start, length);
				}
				if(bschool) {
					bschool = false;
					school = Integer.parseInt(new String(ch, start, length));
				}
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if(id != 0 && fullname != null && school != 0 && bentry) {
					bentry = false;
					competitors.put(id, new Debater(fullname, schools.get(school)));
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, competitorHandler);

		HashMap<Integer, Integer> entryStudents = new HashMap<Integer, Integer>();
		DefaultHandler entryHandler = new DefaultHandler() {

			private boolean bentry_student, bentry, bid;
			private int id, entry;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("ENTRY_STUDENT")) {
					bentry_student = true;
					id = 0;
					entry = 0;
				}
				if(qName.equalsIgnoreCase("ENTRY") && bentry_student)
					bentry = true;
				if(qName.equalsIgnoreCase("ID") && bentry_student)
					bid = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("ENTRY_STUDENT")) {
					bentry_student = false;
					id = 0;
					entry = 0;
				}
				if(qName.equalsIgnoreCase("ENTRY") && bentry_student)
					bentry = false;
				if(qName.equalsIgnoreCase("ID") && bentry_student)
					bid = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bentry) {
					bentry = false;
					entry = Integer.parseInt(new String(ch, start, length));
				}
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if(id != 0 && entry != 0 && bentry_student) {
					bentry_student = false;
					entryStudents.put(id, entry);
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, entryHandler);

		// Getting judges
		HashMap<Integer, Judge> judges = new HashMap<Integer, Judge>();
		DefaultHandler judgeHandler = new DefaultHandler() {

			private boolean bjudge, bid, bschool, bfirst, blast;
			private String first, last;
			private int id, school;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("JUDGE")) {
					bjudge = true;
					first = null;
					last = null;
					id = 0;
					school = 0;
				}
				if(qName.equalsIgnoreCase("FIRST") && bjudge)
					bfirst = true;
				if(qName.equalsIgnoreCase("LAST") && bjudge)
					blast = true;
				if(qName.equalsIgnoreCase("SCHOOL") && bjudge)
					bschool = true;
				if(qName.equalsIgnoreCase("ID") && bjudge)
					bid = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("JUDGE")) {
					bjudge = false;
					first = null;
					last = null;
					id = 0;
					school = 0;
				}
				if(qName.equalsIgnoreCase("FIRST") && bjudge)
					bfirst = false;
				if(qName.equalsIgnoreCase("LAST") && bjudge)
					blast = false;
				if(qName.equalsIgnoreCase("SCHOOL") && bjudge)
					bschool = false;
				if(qName.equalsIgnoreCase("ID") && bjudge)
					bid = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bfirst) {
					bfirst = false;
					first = new String(ch, start, length);
				}
				if(blast) {
					blast = false;
					last = new String(ch, start, length);
				}
				if(bschool) {
					bschool = false;
					school = Integer.parseInt(new String(ch, start, length));
				}
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if(id != 0 && first != null && last != null && school != 0 && bjudge) {
					bjudge = false;
					judges.put(id, new Judge(first + " " + last, schools.get(school)));
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, judgeHandler);

		// Getting round keys / names
		HashMap<Integer, RoundInfo> roundInfo = new HashMap<Integer, RoundInfo>();
		DefaultHandler roundHandler = new DefaultHandler() {

			private boolean bround, brd_name, bpairingscheme, bid;
			private int rd_name, id;
			private String pairingScheme;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("ROUND")) {
					bround = true;
					pairingScheme = null;
					rd_name = 0;
					id = 0;
				}
				if(qName.equalsIgnoreCase("RD_NAME") && bround)
					brd_name = true;
				if(qName.equalsIgnoreCase("PAIRINGSCHEME") && bround)
					bpairingscheme = true;
				if(qName.equalsIgnoreCase("ID") && bround)
					bid = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("ROUND")) {
					bround = false;
					pairingScheme = null;
					rd_name = 0;
					id = 0;
				}
				if(qName.equalsIgnoreCase("RD_NAME") && bround)
					brd_name = false;
				if(qName.equalsIgnoreCase("PAIRINGSCHEME") && bround)
					bpairingscheme = false;
				if(qName.equalsIgnoreCase("ID") && bround)
					bid = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(brd_name) {
					brd_name = false;
					rd_name = Integer.parseInt(new String(ch, start, length));
				}
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if(bpairingscheme) {
					bpairingscheme = false;
					pairingScheme = new String(ch, start, length);
				}
				if(rd_name != 0 && id != 0 && pairingScheme != null && bround) {
					bround = false;
					RoundInfo info = new RoundInfo();
					info.number = rd_name;
					info.elim = pairingScheme.equals("Elim");
					roundInfo.put(id, info);
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, roundHandler);

		// Getting panels
		HashMap<Integer, Round> panels = new HashMap<Integer, Round>(); // Only contains bye and roundInfo
		DefaultHandler panelHandler = new DefaultHandler() {

			private boolean bpanel, bround, bid, bbye;
			private int round, id;
			private Boolean bye = null; // Nullable

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("PANEL")) {
					bpanel = true;
					round = 0;
					id = 0;
					bye = null;
				}
				if(qName.equalsIgnoreCase("ROUND") && bpanel)
					bround = true;
				if(qName.equalsIgnoreCase("ID") && bpanel)
					bid = true;
				if(qName.equalsIgnoreCase("BYE") && bpanel)
					bbye = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("PANEL")) {
					bpanel = false;
					round = 0;
					id = 0;
					bye = null;
				}
				if(qName.equalsIgnoreCase("ROUND") && bpanel)
					bround = false;
				if(qName.equalsIgnoreCase("ID") && bpanel)
					bid = false;
				if(qName.equalsIgnoreCase("BYE") && bpanel)
					bbye = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bround) {
					bround = false;
					round = Integer.parseInt(new String(ch, start, length));
				}
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if(bbye) {
					bbye = false;
					bye = new String(ch, start, length).equals("1");
				}
				if(round != 0 && id != 0 && bye != null && bpanel) {
					bpanel = false;
					Round r = new Round();
					r.roundInfo = roundInfo.get(round);
					r.bye = bye;
					panels.put(id, r);
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, panelHandler);

		// Finally, ballot parsing
		HashMap<Integer, Round> rounds = new HashMap<Integer, Round>(); // Key is panel
		DefaultHandler ballotHandler = new DefaultHandler() {

			private boolean bballot, bid, bdebater, bpanel, bjudge, bside, bbye;
			private int id, debater, panel, judge, side;
			private Boolean bye = null; // Nullable

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT")) {
					bballot = true;
					id = 0;
					debater = 0;
					panel = 0;
					judge = 0;
					side = 0;
					bye = null;
				}
				if(qName.equalsIgnoreCase("ID") && bballot)
					bid = true;
				if(qName.equalsIgnoreCase("ENTRY") && bballot)
					bdebater = true;
				if(qName.equalsIgnoreCase("PANEL") && bballot)
					bpanel = true;
				if(qName.equalsIgnoreCase("JUDGE") && bballot)
					bjudge = true;
				if(qName.equalsIgnoreCase("SIDE") && bballot)
					bside = true;
				if(qName.equalsIgnoreCase("BYE") && bballot)
					bbye = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT")) {
					bballot = false;
					id = 0;
					debater = 0;
					panel = 0;
					judge = 0;
					side = 0;
					bye = null;
				}
				if(qName.equalsIgnoreCase("ID") && bballot)
					bid = false;
				if(qName.equalsIgnoreCase("ENTRY") && bballot)
					bdebater = false;
				if(qName.equalsIgnoreCase("PANEL") && bballot)
					bpanel = false;
				if(qName.equalsIgnoreCase("JUDGE") && bballot)
					bjudge = false;
				if(qName.equalsIgnoreCase("SIDE") && bballot)
					bside = false;
				if(qName.equalsIgnoreCase("BYE") && bballot)
					bbye = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bdebater) {
					bdebater = false;
					debater = Integer.parseInt(new String(ch, start, length));
				}
				if(bpanel) {
					bpanel = false;
					panel = Integer.parseInt(new String(ch, start, length));
				}
				if(bjudge) {
					bjudge = false;
					judge = Integer.parseInt(new String(ch, start, length));
				}
				if(bid) {
					bid = false;
					id = Integer.parseInt(new String(ch, start, length));
				}
				if(bside) {
					bside = false;
					side = Integer.parseInt(new String(ch, start, length));
				}
				if(bbye) {
					bbye = false;
					bye = new String(ch, start, length).equals("1");
				}
				if(debater != 0 && id != 0 && judge != 0 && panel != 0 && bye != null && bballot) {
					bballot = false;
					if(rounds.get(panel) == null) {
						Round round = new Round();
						round.judges = new ArrayList<JudgeBallot>();
						rounds.put(panel, round);
					}
					Round round = rounds.get(panel);
					if (side == 1 && round.aff == null)
						round.aff = competitors.get(debater);
					else if (side == 2 && round.neg == null)
						round.neg = competitors.get(debater);
					else if(side == -1) {
						round.aff = competitors.get(debater);
						round.neg = competitors.get(debater);
					}
					if(round.roundInfo == null)
						round.roundInfo = panels.get(panel).roundInfo;
					if(bye != null && round.bye == null)
						round.bye = panels.get(panel).bye || bye;
					else if(round.bye == null)
						round.bye = panels.get(panel).bye;
					boolean found = false;
					for(JudgeBallot jBallot : round.judges) {
						if (jBallot.judge == null) {
							if (judges.containsValue(null))
								found = true;
						}
						else if(jBallot.judge.equals(judges.get(judge))) {
							found = true;
							jBallot.ballots.add(id);
						}
					}
					if(!found) {
						JudgeBallot jBallot = new JudgeBallot();
						jBallot.ballots = new ArrayList<Integer>();
						jBallot.ballots.add(id);
						jBallot.judge = judges.get(judge);
						round.judges.add(jBallot);
					}
					rounds.put(panel, round); // May be redundant
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, ballotHandler);

		// Round results
		Set<Map.Entry<Integer, Round>> roundsSet = rounds.entrySet();
		DefaultHandler resultHandler = new DefaultHandler() {

			private boolean bballot_score, bballot, bscore_id, bscore, brecipient;
			private int ballot, recipient;
			private String score_id;
			private Double score;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = true;
					ballot = 0;
					score_id = null;
					score = null;
					recipient = 0;
				}
				if(qName.equalsIgnoreCase("BALLOT") && bballot_score)
					bballot = true;
				if(qName.equalsIgnoreCase("SCORE_ID") && bballot_score)
					bscore_id = true;
				if(qName.equalsIgnoreCase("SCORE") && bballot_score)
					bscore = true;
				if(qName.equalsIgnoreCase("RECIPIENT") && bballot_score)
					brecipient = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = false;
					ballot = 0;
					score_id = null;
					score = null;
					recipient = 0;
				}
				if(qName.equalsIgnoreCase("BALLOT") && bballot_score)
					bballot = false;
				if(qName.equalsIgnoreCase("SCORE_ID") && bballot_score)
					bscore_id = false;
				if(qName.equalsIgnoreCase("SCORE") && bballot_score)
					bscore = false;
				if(qName.equalsIgnoreCase("RECIPIENT") && bballot_score)
					brecipient = false;
			}
			public void characters(char ch[], int start, int length) throws SAXException {
				if(bballot) {
					bballot = false;
					ballot = Integer.parseInt(new String(ch, start, length));
				}
				if(bscore_id) {
					bscore_id = false;
					score_id = new String(ch, start, length);
				}
				if(bscore) {
					bscore = false;
					score = Double.parseDouble(new String(ch, start, length));
				}
				if(brecipient) {
					brecipient = false;
					recipient = Integer.parseInt(new String(ch, start, length));
				}
				if(ballot != 0 && recipient != 0 && score_id != null && score != null && bballot_score) {
					if(score_id.equals("WIN")) { // Test to see if this even updates
						for (Map.Entry<Integer, Round> entry : roundsSet) {
							for (JudgeBallot jBallot : entry.getValue().judges)
									if (score == 1 && jBallot.ballots.contains(ballot))
										jBallot.winner = competitors.get(recipient);
						}
					}
					if(score_id.equals("POINTS")) {
						for (Map.Entry<Integer, Round> entry : roundsSet) {
							for (JudgeBallot jBallot : entry.getValue().judges) {
								try {
									if (jBallot.ballots.contains(ballot)) {
										if (competitors.get(entryStudents.get(recipient)).equals(entry.getValue().aff))
											jBallot.affSpeaks = score;
										if (competitors.get(entryStudents.get(recipient)).equals(entry.getValue().neg))
											jBallot.negSpeaks = score;
									}
								} catch(Exception e) {}
							}
						}
					}
				}
			}
		};

		saxParser = factory.newSAXParser();
		stream = new ByteArrayInputStream(baos.toByteArray());
		saxParser.parse(stream, resultHandler);

		HashMap<Integer, String> sqlRoundStrings = roundToSQLFriendlyRound(new ArrayList<Round>(rounds.values()));
		String query = "INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ";
		ArrayList<Object> args = new ArrayList<Object>();
		rounds:
		for(Round round : rounds.values()) {
			ArrayList<Object> a = new ArrayList<Object>();
			if(round.bye) {
				a.add(t.getLink());
				a.add(t.getLink() + "|" + event_id);
				Debater debater = round.aff != null ? round.aff : round.neg != null ? round.neg : null;
				try {
					a.add(debater.getID(sql));
					a.add(debater.getID(sql));
				} catch(SQLException | NullPointerException sqle) {
					continue;
				}
				a.add(sqlRoundStrings.get(round.roundInfo.number));
				a.add(null);
				a.add("B");
				if (!overwrite) {
					try {
						ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
						if (!exists.next()) {
							query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
							args.addAll(a);
						}
						exists.close();
					} catch (SQLException sqle) {
						continue rounds;
					}
				} else {
					query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
					args.addAll(a);
				}
			}
			else {
				int affVotes = 0, negVotes = 0;
				for (JudgeBallot jBallot : round.judges) {
					try {
						if (jBallot.winner.getID(sql) == round.aff.getID(sql))
							affVotes++;
						if (jBallot.winner.getID(sql) == round.neg.getID(sql))
							negVotes++;
					} catch (SQLException | NullPointerException sqle) {
						continue rounds;
					}
				}
				if (round.aff == null || round.neg == null)
					continue;
				a.add(t.getLink());
				a.add(t.getLink() + "|" + event_id);
				try {
					a.add(round.aff.getID(sql));
					a.add(round.neg.getID(sql));
				} catch (SQLException sqle) {
					continue;
				}
				a.add(sqlRoundStrings.get(round.roundInfo.number));
				a.add('A');
				a.add(affVotes + "-" + negVotes);
				if (!overwrite) {
					try {
						ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
						if (!exists.next()) {
							query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
							args.addAll(a);
						}
						exists.close();
					} catch (SQLException sqle) {
						continue rounds;
					}
				} else {
					query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
					args.addAll(a);
				}

				a.clear();

				a.add(t.getLink());
				a.add(t.getLink() + "|" + event_id);
				try {
					a.add(round.neg.getID(sql));
					a.add(round.aff.getID(sql));
				} catch (SQLException sqle) {
					continue;
				}
				a.add(sqlRoundStrings.get(round.roundInfo.number));
				a.add('N');
				a.add(negVotes + "-" + affVotes);
				if (!overwrite) {
					try {
						ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>? AND decision<=>?", a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6));
						if (!exists.next()) {
							query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
							args.addAll(a);
						}
						exists.close();
					} catch (SQLException sqle) {
						continue rounds;
					}
				} else {
					query += "((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ";
					args.addAll(a);
				}
			}
		}

		try {
			if (!query.equals("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ")) {
				query = query.substring(0, query.lastIndexOf(", "));
				sql.executePreparedStatement(query, args.toArray());

				String judgeQuery = "INSERT INTO ld_judges (round, judge_id, decision, aff_speaks, neg_speaks) VALUES ";
				ArrayList<Object> judgeArgs = new ArrayList<Object>();
				for (Round round : rounds.values()) {
					for (JudgeBallot jBallot : round.judges) {
						ArrayList<Object> a = new ArrayList<Object>();
						try {
							ResultSet idStatement = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE tournament=(SELECT id FROM tournaments WHERE link=?) AND absUrl<=>? AND debater=? AND against=? AND round<=>? AND side<=>?", t.getLink(), t.getLink() + "|" + event_id, jBallot.winner.getID(sql), jBallot.winner.getID(sql) != round.aff.getID(sql) ? round.aff.getID(sql) : round.neg.getID(sql), sqlRoundStrings.get(round.roundInfo.number), (jBallot.winner.getID(sql) == round.aff.getID(sql) ? 'A' : (jBallot.winner.getID(sql) == round.neg.getID(sql) ? 'N' : null)));
							if(!idStatement.next())
								continue;
							a.add(idStatement.getInt(1));
							idStatement.close();
						} catch(NullPointerException npe) {
							continue;
						}
						try {
							a.add(jBallot.judge.getID(sql));
							a.add((jBallot.winner.getID(sql) == round.aff.getID(sql) ? 'A' : (jBallot.winner.getID(sql) == round.neg.getID(sql) ? 'N' : null)));
						} catch (SQLException | NullPointerException sqle) {
							continue;
						}
						if(jBallot.affSpeaks != 0)
							a.add(jBallot.affSpeaks);
						else
							a.add(null);
						if(jBallot.negSpeaks != 0)
							a.add(jBallot.negSpeaks);
						else
							a.add(null);

						if (!overwrite) {
							try {
								ResultSet exists = sql.executeQueryPreparedStatement("SELECT * FROM ld_judges WHERE round=? AND judge_id=?", a.get(0), a.get(1));
								if (!exists.next()) {
									judgeQuery += "(?, ?, ?, ?, ?), ";
									judgeArgs.addAll(a);
								}
								exists.close();
							} catch (SQLException sqle) {
								continue;
							}
						} else {
							judgeQuery += "(?, ?, ?, ?, ?), ";
							judgeArgs.addAll(a);
						}

					}
				}

				if(!judgeQuery.equals("INSERT INTO ld_judges (round, judge_id, decision, aff_speaks, neg_speaks) VALUES ")) {
					judgeQuery = judgeQuery.substring(0, judgeQuery.lastIndexOf(", "));
					sql.executePreparedStatement(judgeQuery, judgeArgs.toArray());
				}

				log.log(TABROOM, t.getName() + " updated.");
			} else {
				log.log(TABROOM, t.getName() + " is up to date.");
			}
		} catch(Exception ex) {
			log.error(ex);
			ex.printStackTrace();
			log.fatal("Could not update " + t.getName() + " - " + ex);
		}

	}

	private HashMap<Integer, String> roundToSQLFriendlyRound(List<Round> r) {
		ArrayList<Round> rounds = new ArrayList<Round>();
		for(Round round : r) {
			boolean contains = false;
			for(Round g : rounds)
				if(round.roundInfo.number == g.roundInfo.number)
					contains = true;
			if(!contains)
				rounds.add(round);
		}
		String[] elimsStrings = {"TO", "DO", "O", "Q", "S", "F"};
		Collections.sort(rounds, new Comparator<Round>() {
			public int compare(Round o1, Round o2) {
				return o1.roundInfo.number - o2.roundInfo.number;
			}
		});
		ArrayList<Pair<Round, String>> elims = new ArrayList<Pair<Round, String>>();
		for(int i = 0;i<rounds.size();i++)
			if(rounds.get(i).roundInfo.elim) {
				elims.add(Pair.of(rounds.get(i), null));
				rounds.remove(i--);
			}
		for(int i = 0;i<elims.size();i++)
			elims.set(i, Pair.of(elims.get(i).getLeft(), elimsStrings[elimsStrings.length - (elims.size() - i)]));
		HashMap<Integer, String> ret = new HashMap<Integer, String>();
		for(Round round : rounds)
			ret.put(round.roundInfo.number, String.valueOf(round.roundInfo.number));
		for(Pair<Round, String> pair : elims)
			ret.put(pair.getLeft().roundInfo.number, pair.getRight());
		return ret;
	}
}
