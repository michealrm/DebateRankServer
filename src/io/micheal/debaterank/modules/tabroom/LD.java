package io.micheal.debaterank.modules.tabroom;

import static io.micheal.debaterank.util.DebateHelper.TABROOM;
import static io.micheal.debaterank.util.DebateHelper.getDebater;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.parsers.*;
import javax.xml.stream.XMLStreamException;

import io.micheal.debaterank.*;
import io.micheal.debaterank.util.DataSource;
import io.micheal.debaterank.util.TournamentRunnable;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.SQLHelper;

// XML Parsing sucks.
public class LD extends Module {

    private static final int MAX_RETRY = 5;

    private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	private final boolean overwrite;
	private ArrayList<TournamentRunnable> runnables;

	public LD(SQLHelper sql, Logger log, ArrayList<Tournament> tournaments, WorkerPool manager, DataSource ds) {
		super(sql, log, ds);
		this.tournaments = tournaments;
		this.manager = manager;
		runnables = new ArrayList<>();

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
			if(t.getName().contains("Strake Jesuit LD"))
			manager.newModule(new Runnable() {
				private SQLHelper sql;
				public void run() {
					try {
						sql = new SQLHelper(ds.getBds().getConnection());

						String tournIDStr = "";
						int index = t.getLink().indexOf("tourn_id=") + 9;
						while(index < t.getLink().length()) {
							try {
								Integer.parseInt(Character.toString(t.getLink().toCharArray()[index]));
							}
							catch(NumberFormatException e) {
								break;
							}
							tournIDStr += Character.toString(t.getLink().toCharArray()[index]);
							++index;
						}

						int tourn_id = Integer.parseInt(tournIDStr);

						String[] split = t.getDate().split("-| ");
						URL url = new URL("https://www.tabroom.com/api/current_tournaments.mhtml?timestring=" + split[0] + "-" + split[1] + "-" + split[2] + "T12:00:00");

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
											ResultSet set = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE absUrl=?", t.getLink() + "|" + event_id); // TODO: Temp

											if(event_id == 57528) {
												log.log(TABROOM, "Queuing " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event_id);
												try {
													enterTournament(sql, t, factory, tourn_id, event_id);
												} catch (Exception e) {
													e.printStackTrace();
												}
											}
											else {
												log.log(TABROOM, t.getName() + " is up to date.");
												return;
											}
										} catch(SQLException sqle) {
											sqle.printStackTrace();
											log.error(sqle);
											log.fatal("Could not update " + t.getName() + " - " + sqle.getErrorCode());
											return;
										}
									}
								}
							}
						};

						InputStream iStream = url.openStream();
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						byte[] buffer = new byte[1024];
						int len;
						while ((len = iStream.read(buffer)) > -1 ) {
							baos.write(buffer, 0, len);
						}
						baos.flush();
						iStream.close();

						InputStream stream = new ByteArrayInputStream(baos.toByteArray());
						saxParser.parse(stream, handler);

						stream.close();
						baos.close();
						try {
							sql.close();
						} catch(SQLException sqle) {
							log.error(sqle);
							log.error("Could not close SQLHelper");
						}

					} catch(Exception e) {
						e.printStackTrace();
						if(sql != null) {
							try {
								sql.close();
							} catch(SQLException sqle) {
								log.error(sqle);
								log.error("Could not close SQLHelper");
							}
						}
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

	}

	private ThreadLocal<Integer> size = new ThreadLocal<Integer>(); // for getTournamentRoundEntries

	private int getTournamentRoundEntries(SAXParser saxParser, InputStream stream, int tourn_id, int event_id) throws XMLStreamException, IOException, ParserConfigurationException, SAXParseException, SAXException {
		size.set(0);

		DefaultHandler resultHandler = new DefaultHandler() {

			private boolean bballot_score, bscore_id;
			private String score_id;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if (qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = true;
					score_id = null;
				}
				if (qName.equalsIgnoreCase("SCORE_ID") && bballot_score)
					bscore_id = true;
			}

			public void endElement(String uri, String localName, String qName) throws SAXException {
				if (qName.equalsIgnoreCase("BALLOT_SCORE")) {
					bballot_score = false;
					score_id = null;
				}
				if (qName.equalsIgnoreCase("SCORE_ID") && bballot_score)
					bscore_id = false;
			}

			public void characters(char ch[], int start, int length) throws SAXException {
				if (bscore_id) {
					bscore_id = false;
					score_id = new String(ch, start, length);
				}
				if (score_id != null && bballot_score) {
					bballot_score = false;
					if (score_id.equals("WIN")) {
						size.set(size.get() + 1);
					}
				}
			}
		};
		try {
			saxParser.parse(stream, resultHandler);
		} catch(SAXParseException e) {
			throw new SAXParseException(e.getMessage(), e.getPublicId(), e.getSystemId(), e.getLineNumber(), e.getColumnNumber());
		}
		int localSize = size.get().intValue();

		return localSize;
	}

	private void enterTournament(SQLHelper sql, Tournament t, SAXParserFactory factory, int tourn_id, int event_id) throws ParserConfigurationException,IOException, SAXException, XMLStreamException {
		SAXParser saxParser = factory.newSAXParser();
        String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id;
        BufferedInputStream iStream = null;
        int k = 0;
        boolean saxpe = false;
        do {
        	saxpe = false;
			for (int i = 0; i < MAX_RETRY; i++) {
				URL url = new URL(endpoint);
				HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
				if (urlConnection.getResponseCode() != 200) {
					log.warn("Bad response code: " + urlConnection.getResponseCode());
				}
				int length = Integer.parseInt(Optional.ofNullable(urlConnection.getHeaderField("Content-length")).orElse("65535"));
				if (length > 0 && urlConnection.getResponseCode() == 200) {
					iStream = new BufferedInputStream(urlConnection.getInputStream()) {
						@Override
						public void close() throws IOException {
							//ignoring close as we going read it several times
						}
					};
					iStream.mark(length + 1);
					break;
				}
				log.warn("Received empty response from server. Retry in 1 sec");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (iStream == null) {
				throw new RuntimeException("Cannot load xml from server");
			}
			try {
				if (getTournamentRoundEntries(saxParser, iStream, tourn_id, event_id) == 0) {
					log.log(TABROOM, "Skipping " + t.getName());
					return;
				}
			} catch (SAXParseException e) {
				saxpe = true;
				if(k == MAX_RETRY) {
					iStream.close();
					throw new SAXParseExceptionTournaments(endpoint, e.getMessage(), e.getPublicId(), e.getSystemId(), e.getLineNumber(), e.getColumnNumber());
				}
				//throw new SAXParseExceptionTournaments(endpoint, e.getMessage(), e.getPublicId(), e.getSystemId(), e.getLineNumber(), e.getColumnNumber());
			}
		} while(saxpe && k++ < MAX_RETRY);

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
        iStream.reset();
		saxParser.parse(iStream, schoolHandler);

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

        iStream.reset();
		saxParser.parse(iStream, competitorHandler);

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
        iStream.reset();
		saxParser.parse(iStream, entryHandler);

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
        iStream.reset();
		saxParser.parse(iStream, judgeHandler);

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

        iStream.reset();
		saxParser.parse(iStream, roundHandler);

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

        iStream.reset();
		saxParser.parse(iStream, panelHandler);

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

        iStream.reset();
		saxParser.parse(iStream, ballotHandler);

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

        iStream.reset();
		saxParser.parse(iStream, resultHandler);
		iStream.close();
		iStream = null;

		HashMap<Integer, String> sqlRoundStrings = roundToSQLFriendlyRound(new ArrayList<Round>(rounds.values()));
		StringBuilder query = new StringBuilder("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ");
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
							query.append("((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ");
							args.addAll(a);
						}
						exists.close();
					} catch (SQLException sqle) {
						continue rounds;
					}
				} else {
					query.append("((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ");
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
							query.append("((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ");
							args.addAll(a);
						}
						exists.close();
					} catch (SQLException sqle) {
						continue rounds;
					}
				} else {
					query.append("((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ");
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
							query.append("((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ");
							args.addAll(a);
						}
						exists.close();
					} catch (SQLException sqle) {
						continue rounds;
					}
				} else {
					query.append("((SELECT id FROM tournaments WHERE link=?), ?, ?, ?, ?, ?, ?), ");
					args.addAll(a);
				}
			}
		}

		try {
			String queryString = query.toString();
			if (!queryString.equals("INSERT INTO ld_rounds (tournament, absUrl, debater, against, round, side, decision) VALUES ")) {
				queryString = query.substring(0, query.lastIndexOf(", "));
				query = null;
				sql.executePreparedStatement(queryString, args.toArray());
				log.log(TABROOM, t.getName() + " rounds inserted.");

				HashMap<Integer, Round> ld_rounds = new HashMap<>();
				ResultSet idStatement = sql.executeQueryPreparedStatement("SELECT ld.id, d1.first, d1.middle, d1.last, d1.surname, school1.name, d2.first, d2.middle, d2.last, d2.surname, school1.name, round, side FROM ld_rounds ld JOIN debaters AS d1 ON ld.debater=d1.id JOIN debaters AS d2 ON ld.debater=d2.id JOIN schools AS school1 ON d1.school=school1.id JOIN schools AS school2 ON school2.id=d2.school WHERE absUrl<=>?", t.getLink() + "|" + event_id);
				while(idStatement.next()) {
					Round round = new Round();
					Debater d1 = new Debater(idStatement.getString(2), idStatement.getString(3), idStatement.getString(4), idStatement.getString(5), idStatement.getString(6));
					Debater d2 = new Debater(idStatement.getString(7), idStatement.getString(8), idStatement.getString(9), idStatement.getString(10), idStatement.getString(11));
					round.aff = idStatement.getString(13) == null || idStatement.getString(13).equals("A") ? d1 : d2;
					round.neg = idStatement.getString(13) == null || idStatement.getString(13).equals("A") ? d2 : d1;
					round.roundInfo = new RoundInfo();
					round.roundInfo.letter = idStatement.getString(12).charAt(0);
					ld_rounds.put(Integer.parseInt(idStatement.getString(1)), round);
				}
				System.out.println("but does it make it here?");
				StringBuilder judgeQuery = new StringBuilder("INSERT INTO ld_judges (round, judge_id, decision, aff_speaks, neg_speaks) VALUES ");
				ArrayList<Object> judgeArgs = new ArrayList<Object>();
				for (Round round : rounds.values()) {
					ArrayList<Object> a = new ArrayList<Object>();
					for (JudgeBallot jBallot : round.judges) {
						Integer key = null;
						for(Map.Entry<Integer, Round> entry : ld_rounds.entrySet()) {
							try {
								if(entry.getValue().aff.equals(round.aff) && entry.getValue().neg.equals(round.neg) && Character.toString(entry.getValue().roundInfo.letter).equals(sqlRoundStrings.get(round.roundInfo.number))) {
									key = entry.getKey();
									a.add(key);
								}
							} catch (NullPointerException npe) {
								continue;
							}
						}
						if(key == null)
							continue;
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
									judgeQuery.append("(?, ?, ?, ?, ?), ");
									judgeArgs.addAll(a);
								}
								exists.close();
							} catch (SQLException sqle) {
								continue;
							}
						} else {
							judgeQuery.append("(?, ?, ?, ?, ?), ");
							judgeArgs.addAll(a);
						}

					}
				}

				String judgeQueryString = judgeQuery.toString();
				if(!judgeQueryString.equals("INSERT INTO ld_judges (round, judge_id, decision, aff_speaks, neg_speaks) VALUES ")) {
					judgeQueryString = judgeQuery.substring(0, judgeQuery.lastIndexOf(", "));
					try {
						sql.executePreparedStatement(judgeQueryString, judgeArgs.toArray());
					} catch(SQLException e) {
						System.out.println(args);
						e.printStackTrace();
					}
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
