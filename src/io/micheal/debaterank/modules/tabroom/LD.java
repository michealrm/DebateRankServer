package io.micheal.debaterank.modules.tabroom;

import static io.micheal.debaterank.util.DebateHelper.TABROOM;
import static io.micheal.debaterank.util.DebateHelper.getDebater;
import static io.micheal.debaterank.util.DebateHelper.tournamentExists;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
											ResultSet set = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE absUrl=? LIMIT 0,1", t.getLink() + "|" + event_id); // TODO: Temp

											if(true) {
												try {

													String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id + "&output=json";
													BufferedInputStream iStream = null;
													int k = 0;
													boolean ioe = false;
													do {
														ioe = false;
														for (int i = 0; i < MAX_RETRY; i++) {
															URL url = new URL(endpoint);
															HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
															if (urlConnection.getResponseCode() != 200) {
																log.warn("Bad response code: " + urlConnection.getResponseCode());
															}
															int l = Integer.parseInt(Optional.ofNullable(urlConnection.getHeaderField("Content-length")).orElse("65535"));
															if (l > 0 && urlConnection.getResponseCode() == 200) {
																iStream = new BufferedInputStream(urlConnection.getInputStream()) {
																	@Override
																	public void close() throws IOException {
																		//ignoring close as we going read it several times
																	}
																};
																iStream.mark(l + 1);
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
															int rounds = getTournamentRoundEntries(iStream);
															if (rounds == 0 || tournamentExists(t.getLink() + "|" + event_id, rounds, sql, "ld_rounds")) {
																log.log(TABROOM, "Skipping " + t.getName());
																return;
															}
														} catch (IOException e) {
															ioe = true;
															if(k == MAX_RETRY) {
																iStream.close();
																throw new IOException(e.getMessage(), e.getCause());
															}
															//throw new SAXParseExceptionTournaments(endpoint, e.getMessage(), e.getPublicId(), e.getSystemId(), e.getLineNumber(), e.getColumnNumber());
														}
													} while(ioe && k++ < MAX_RETRY);

													log.log(TABROOM, "Queuing " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event_id);
													enterTournament(iStream, sql, t, factory, tourn_id, event_id);
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

	private int getTournamentRoundEntries(InputStream stream) throws IOException {
		int size = 0;

		long one = System.currentTimeMillis();
		JSONObject jsonObject = readJsonFromInputStream(stream);

		JSONArray ballot_score = jsonObject.getJSONArray("ballot_score");
		for(int i = 0; i<ballot_score.length();i++) {
			JSONObject jObject = ballot_score.getJSONObject(i);
			if(jObject.getString("SCORE_ID").equals("WIN") && jObject.getInt("SCORE") == 1)
				size++;
		}

		return size;
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public static JSONObject readJsonFromInputStream(InputStream is) throws IOException, JSONException {
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			JSONObject json = new JSONObject(jsonText);
			return json;
		} finally {
			is.close();
		}
	}

	private void enterTournament(BufferedInputStream iStream, SQLHelper sql, Tournament t, SAXParserFactory factory, int tourn_id, int event_id) throws ParserConfigurationException,IOException, SAXException, XMLStreamException {
		SAXParser saxParser = factory.newSAXParser();
		JSONObject jsonObject = readJsonFromInputStream(iStream);
		iStream.close();
		iStream = null;

		log.log(TABROOM, "Updating " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event_id);

		//Overwrite
		try {
			if (overwrite) {
				sql.executePreparedStatementArgs("DELETE FROM ld_judges WHERE round IN (SELECT id FROM ld_rounds WHERE absUrl=?)", t.getLink() + "|" + event_id);
				sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", t.getLink() + "|" + event_id);
			}
		} catch(SQLException sqle) {
			sqle.printStackTrace();
			log.error(sqle);
			log.fatal("Could not update " + t.getName() + " - " + sqle.getErrorCode());
			return;
		}

		// Getting schools
		HashMap<Integer, String> schools = new HashMap<Integer, String>();
		JSONArray jsonSchool = jsonObject.getJSONArray("school");
		for(int i = 0;i<jsonSchool.length();i++) {
			JSONObject jObject = jsonSchool.getJSONObject(i);
			int id = jObject.getInt("ID");
			String schoolName = jObject.getString("SCHOOLNAME");
			schools.put(id, schoolName);
		}

		// Getting competitors
		HashMap<Integer, Debater> competitors = new HashMap<Integer, Debater>();
		JSONArray jsonEntry = jsonObject.getJSONArray("entry");
		for(int i = 0;i<jsonEntry.length();i++) {
			JSONObject jObject = jsonEntry.getJSONObject(i);
			String fullname = jObject.getString("FULLNAME");
			int id = jObject.getInt("ID");
			String school = schools.get(jObject.getInt("SCHOOL"));
			competitors.put(id, new Debater(fullname, school));
		}

		// Getting entry students
		HashMap<Integer, Integer> entryStudents = new HashMap<Integer, Integer>();
		JSONArray jsonEntry_student = jsonObject.getJSONArray("entry");
		for(int i = 0;i<jsonEntry_student.length();i++) {
			JSONObject jObject = jsonEntry_student.getJSONObject(i);
			int id = jObject.getInt("ID");
			int entry = jObject.getInt("SCHOOL");
			entryStudents.put(id, entry);
		}

		// Getting judges
		HashMap<Integer, Judge> judges = new HashMap<Integer, Judge>();
		JSONArray jsonJudge = jsonObject.getJSONArray("judge");
		for(int i = 0;i<jsonJudge.length();i++) {
			JSONObject jObject = jsonJudge.getJSONObject(i);
			int id = jObject.getInt("ID");
			String first = jObject.getString("FIRST");
			String last = jObject.getString("LAST");
			String school = schools.get(jObject.getString("SCHOOL"));
			judges.put(id, new Judge(first + " " + last, school));

		}

		// Getting round keys / names
		HashMap<Integer, RoundInfo> roundInfo = new HashMap<Integer, RoundInfo>();
		JSONArray jsonRound = jsonObject.getJSONArray("round");
		for(int i = 0;i<jsonRound.length();i++) {
			JSONObject jObject = jsonRound.getJSONObject(i);
			int id = jObject.getInt("ID");
			int rd_name = jObject.getInt("RD_NAME");
			String pairingScheme = jObject.getString("PAIRINGSCHEME");
			RoundInfo info = new RoundInfo();
			info.number = rd_name;
			info.elim = pairingScheme.equals("Elim");
			roundInfo.put(id, info);
		}

		// Getting panels
		HashMap<Integer, Round> panels = new HashMap<Integer, Round>(); // Only contains bye and roundInfo
		JSONArray jsonPanel = jsonObject.getJSONArray("panel");
		for(int i = 0;i<jsonPanel.length();i++) {
			JSONObject jObject = jsonPanel.getJSONObject(i);
			int id = jObject.getInt("ID");
			int round = jObject.getInt("ROUND");
			boolean bye = jObject.getInt("BYE") == 1;
			Round r = new Round();
			r.roundInfo = roundInfo.get(round);
			r.bye = bye;
			panels.put(id, r);
		}

		// Finally, ballot parsing
		HashMap<Integer, Round> rounds = new HashMap<Integer, Round>(); // Key is panel
		JSONArray jsonBallot = jsonObject.getJSONArray("ballot");
		for(int i = 0;i<jsonBallot.length();i++) {
			JSONObject jObject = jsonBallot.getJSONObject(i);
			int id = jObject.getInt("ID");
			int debater = jObject.getInt("ENTRY");
			int panel = jObject.getInt("PANEL");
			int judge = jObject.getInt("JUDGE");
			int side = jObject.getInt("SIDE");
			Boolean bye = null;
			if(jObject.has("BYE"))
				bye = jObject.getInt("BYE") == 1;

			// TODO: Rewrite this
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
			if(!found && judges.get(judge) != null) {
				JudgeBallot jBallot = new JudgeBallot();
				jBallot.ballots = new ArrayList<Integer>();
				jBallot.ballots.add(id);
				jBallot.judge = judges.get(judge);
				round.judges.add(jBallot);
			}
			rounds.put(panel, round); // May be redundant
		}

		// Round results
		Set<Map.Entry<Integer, Round>> roundsSet = rounds.entrySet();
		JSONArray jsonBallot_score = jsonObject.getJSONArray("ballot_score");
		for(int i = 0;i<jsonBallot_score.length();i++) {
			JSONObject jObject = jsonBallot_score.getJSONObject(i);
			int ballot = jObject.getInt("BALLOT");
			int recipient = jObject.getInt("RECIPIENT");
			String score_id = jObject.getString("SCORE_ID");
			double score = jObject.getDouble("SCORE");

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
