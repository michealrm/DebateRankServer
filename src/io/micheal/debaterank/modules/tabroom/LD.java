package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.School;
import io.micheal.debaterank.Tournament;
import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.SQLHelper;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;

import javax.xml.namespace.QName;
import javax.xml.parsers.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.*;

import static io.micheal.debaterank.util.DebateHelper.JOT;
import static io.micheal.debaterank.util.DebateHelper.getDebaterID;
import static io.micheal.debaterank.util.DebateHelper.tournamentExists;

// XML Parsing sucks.
public class LD extends Module {

	private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	private final boolean overwrite;

	public LD(SQLHelper sql, Logger log, ArrayList<Tournament> tournaments, WorkerPool manager) {
		super(sql, log);
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
		for(Tournament t : tournaments) {
			long one = System.currentTimeMillis();
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



						String[] split = t.getDate().split("\\/");
						URL url = new URL("https://www.tabroom.com/api/current_tournaments.mhtml?timestring=" + split[2] + "-" + split[0] + "-" + split[1] + "T12:00:00");

						SAXParserFactory factory = SAXParserFactory.newInstance();
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
											if (getTournamentRoundEntries(tourn_id, event_id) == 0)
												return;

											// If we have the same amount of entries, then do not check
											//if (tournamentExists(t.getLink() + "|" + event_id, getTournamentRoundEntries(tourn_id, event_id), sql, "ld_rounds"))
											//	log.log(JOT, t.getName() + " prelims is up to date.");

											// Overwrite
											//if (overwrite)
											//	sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", t.getLink() + "|" + event_id);

											System.out.println(System.currentTimeMillis() - one);
										} catch (XMLStreamException xmlse) {
										} catch (IOException ioe) {}
										catch(ParserConfigurationException pce) {}
									}
								}
							}
						};

						saxParser.parse(url.openStream(), handler);

					} catch(IOException ioe) {}
					catch (SAXException e) {
					} catch (ParserConfigurationException e) {
					}
					//catch(SQLException sqle) {}
				}
			});
		}
	}

	private ThreadLocal<Integer> size = new ThreadLocal<Integer>(); // for getTournamentRoundEntries

	public int getTournamentRoundEntries(int tourn_id, int event_id) throws XMLStreamException, IOException, SAXException, ParserConfigurationException {
		size.set(0);
		URL url = new URL("https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id);
		URLConnection connection = url.openConnection();
		InputStream stream = connection.getInputStream();
System.out.println("Size:" + size.get());
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setFeature("http://xml.org/sax/features/namespaces", false);
		factory.setFeature("http://xml.org/sax/features/validation", false);
		SAXParser saxParser = factory.newSAXParser();
		DefaultHandler handler = new DefaultHandler() {

			private boolean bballot;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT"))
					bballot = true;
				if(qName.equalsIgnoreCase("ID") && bballot) {
					bballot = false;
					size.set(size.get().intValue()+1);
				}
			}

			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT"))
					bballot = false;
			}
		};

		long two = System.currentTimeMillis();
		saxParser.parse(stream, handler);
		System.out.println("Time to complete:" + (System.currentTimeMillis() - two));
		int localSize = size.get().intValue();

		System.out.println(localSize + " " + url);
		return localSize;
	}

	private XMLEvent getNextStartElement(XMLEventReader xmlEventReader) throws XMLStreamException {
		XMLEvent event = null;
		do {
			event = xmlEventReader.nextTag();
		} while(xmlEventReader.hasNext() && !event.isStartElement());
		return event;
	}

	public void enterTournament(int tourn_id, int event_id) throws ParserConfigurationException,IOException, SAXException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser saxParser = factory.newSAXParser();
		URL url = new URL("https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id);

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

		saxParser.parse(url.openStream(), schoolHandler);

		// Getting competitors
		HashMap<Integer, Debater> competitors = new HashMap<Integer, Debater>();
		DefaultHandler entryHandler = new DefaultHandler() {

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

		saxParser.parse(url.openStream(), entryHandler);

		// Get all ids
		for(Debater debater : competitors.values()) {
			try {
				debater.setID(getDebaterID(sql, debater));
			} catch(SQLException e) {}
		}

		// Getting judges
		HashMap<Integer, Debater> judges = new HashMap<Integer, Debater>();
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
					competitors.put(id, new Debater(first + " " + last, schools.get(school)));
				}
			}
		};

		saxParser.parse(url.openStream(), judgeHandler);

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

		saxParser.parse(url.openStream(), roundHandler);

		// Getting panels
		HashMap<Integer, RoundInfo> panels = new HashMap<Integer, RoundInfo>();
		DefaultHandler panelHandler = new DefaultHandler() {

			private boolean bpanel, bround, bid;
			private int round, id;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("PANEL")) {
					bpanel = true;
					round = 0;
					id = 0;
				}
				if(qName.equalsIgnoreCase("ROUND") && bpanel)
					bround = true;
				if(qName.equalsIgnoreCase("ID") && bpanel)
					bid = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("PANEL")) {
					bpanel = false;
					round = 0;
					id = 0;
				}
				if(qName.equalsIgnoreCase("ROUND") && bpanel)
					bround = false;
				if(qName.equalsIgnoreCase("ID") && bpanel)
					bid = false;
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
				if(round != 0 && id != 0 && bpanel) {
					bpanel = false;
					panels.put(id, roundInfo.get(round));
				}
			}
		};

		saxParser.parse(url.openStream(), panelHandler);

		// Finally, ballot parsing
		HashMap<Integer, Round> rounds = new HashMap<Integer, Round>(); // Key is panel
		DefaultHandler ballotHandler = new DefaultHandler() {

			private boolean bballot, bid, bdebater, bpanel, bjudge, bside, bbye;
			private int id, debater, panel, judge, side;
			private Boolean bye; // Nullable

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
					bye = Integer.parseInt(new String(ch, start, length)) == 1;
				}
				if(debater != 0 && id != 0 && judge != 0 && id != 0 && bye != null && bballot) {
					bballot = false;
					if(rounds.get(panel) == null) {
						Round round = new Round();
						rounds.put(panel, round);
					}
					Round round = rounds.get(panel);
					if (side == 1 && round.aff == null)
						round.aff = competitors.get(debater);
					else if (side == 2 && round.neg == null)
						round.neg = competitors.get(debater);
					if(round.bye != null)
						round.bye = bye;
					round.judges.add(Pair.of(id, Pair.of(judges.get(judge), null)));
					rounds.put(panel, round); // May be redundant
				}
			}
		};

		saxParser.parse(url.openStream(), ballotHandler);

		// Round results
		DefaultHandler resultHandler = new DefaultHandler() {

			private boolean bballot_score, bballot, bscore_id, bscore;
			private int ballot;
			private String score_id;
			private Integer score;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = true;
					ballot = 0;
					score_id = null;
					score = null;
				}
				if(qName.equalsIgnoreCase("BALLOT") && bballot)
					bballot = true;
				if(qName.equalsIgnoreCase("SCORE_ID") && bballot)
					bscore_id = true;
				if(qName.equalsIgnoreCase("SCORE") && bballot)
					bscore = true;
			}
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = false;
					ballot = 0;
					score_id = null;
					score = null;
				}
				if(qName.equalsIgnoreCase("BALLOT") && bballot)
					bballot = false;
				if(qName.equalsIgnoreCase("SCORE_ID") && bballot)
					bscore_id = false;
				if(qName.equalsIgnoreCase("SCORE") && bballot)
					bscore = false;
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
					score = Integer.parseInt(new String(ch, start, length));
				}
				if(ballot != 0 && score_id != null && score != null && bballot_score) {

				}
			}
		};

		saxParser.parse(url.openStream(), resultHandler);

	}
}
