package io.micheal.debaterank;

import io.micheal.debaterank.modules.ModuleManager;
import io.micheal.debaterank.modules.PoolSizeException;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.modules.WorkerPoolManager;
import io.micheal.debaterank.modules.nsda.Schools;
import io.micheal.debaterank.util.DataSource;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.RatingsComparator;
import io.micheal.debaterank.util.SQLHelper;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.goochjs.glicko2.Rating;
import org.goochjs.glicko2.RatingCalculator;
import org.goochjs.glicko2.RatingPeriodResults;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class Main {

	public Logger log;
	public boolean active = true;
	private DataSource ds;
	private Configuration config;
	public static HashMap<Debater, Debater> pointers; // From, To
	private static ArrayList<Debater> debaters;
	private static ArrayList<School> schools;
	private static ArrayList<Judge> judges;
	private String url, username, password;

	public static ArrayList<Debater> getDebaters(SQLHelper sql) throws SQLException {
		if(debaters == null)
			debaters = DebateHelper.getDebaters(sql);
		return debaters;
	}

	public static ArrayList<School> getSchools(SQLHelper sql) throws SQLException {
		if(schools == null)
			schools = DebateHelper.getSchools(sql);
		return schools;
	}

	public static ArrayList<Judge> getJudges(SQLHelper sql) throws SQLException {
		if(judges == null)
			judges = DebateHelper.getJudges(sql);
		return judges;
	}

	public static void main(String[] args) {
		new Main().run();
	}
	
	public Main() {
		log = LogManager.getLogger(Main.class);
		log.debug("Instantiated logger");

		Configurations configs = new Configurations();
		try
		{
		    config = configs.properties(new File("config.properties"));
		    String host = config.getString("db.host");
		    String name = config.getString("db.name");
		    String user = config.getString("db.username");
		    String pass = config.getString("db.password");
		    int port = config.getInt("db.port");
			int pool = config.getInt("pool");

			ds = new DataSource("jdbc:mysql://" + host + ":" + port + "/" + name, user, pass, pool);
		} catch (Exception e) {
			log.error(e);
			System.exit(1);
		}
		try {
			pointers = new HashMap<Debater, Debater>();
			SQLHelper sql = new SQLHelper(ds.getBds().getConnection());
			ResultSet set = sql.executeQuery("SELECT old_first, old_middle, old_last, old_surname, old_school, first, middle, last, surname, school, `to` FROM pointers p JOIN debaters AS d ON d.id=p.to");
			while(set.next()) {
				Debater one = new Debater(set.getString(1), set.getString(2), set.getString(3), set.getString(4), set.getString(5));
				Debater two = new Debater(set.getString(6), set.getString(7), set.getString(8), set.getString(9), DebateHelper.getSchool(sql, set.getInt(10)));
				two.setID(set.getInt(11));
				pointers.put(one, two);
			}
			sql.close();
		} catch (SQLException | ClassNotFoundException e) {
			e.printStackTrace();
			log.error(e);
			log.error("Could not load pointers");
		}
	}
	
	public void run() {
		try {

			///////////////
			// Variables //
			///////////////

			ModuleManager moduleManager = new ModuleManager();
			WorkerPoolManager workerManager = new WorkerPoolManager();
			SQLHelper sql = new SQLHelper(ds.getBds().getConnection());

//				// TEMP CALCULATIONS
//
//				try {
//					int aff = 0, neg = 0;
//					ResultSet set = sql.executeQuery("SELECT side, decision FROM ld_rounds JOIN tournaments as t ON t.id=ld_rounds.tournament WHERE t.date>'2016-07-01 00:00:00.000' AND absUrl like '%tabroom%'");
//					while(set.next()) {
//						if(set.getString(1) != null && set.getString(2) != null) {
//							if(set.getString(1).equals("A") && set.getString(2).equals("1-0"))
//								aff++;
//							else if(set.getString(1).equals("A") && set.getString(2).equals("0-1"))
//								neg++;
//							System.out.println("Aff: " + aff);
//							System.out.println("Neg: " + neg);
//						}
//
//
//
//					}
//
//					System.out.println("\nFinal");
//					System.out.println("Aff: " + aff);
//					System.out.println("Neg: " + neg);
//
//				} catch(SQLException e) {}

			/////////
			// JOT //
			/////////

			ArrayList<Tournament> jotTournaments = null;
			try {
				// Get seasons so we can iterate through all the jotTournaments
				Document tlist = Jsoup.connect("http://www.joyoftournaments.com/results.asp").get();
				ArrayList<String> years = new ArrayList<String>();
				for (Element select : tlist.select("select"))
					if (select.attr("name").equals("season"))
						for (Element option : select.select("option"))
							years.add(option.attr("value"));

				// Get all the tournaments
				jotTournaments = new ArrayList<Tournament>();
				for (String year : years) {
					Document tournamentDoc = Jsoup.connect("http://www.joyoftournaments.com/results.asp").timeout(10 * 1000)
							.data("state", "")
							.data("month", "0")
							.data("season", year)
							.post();

					Element table = tournamentDoc.select("table.bc").first();
					Elements rows = table.select("tr");
					for (int i = 1; i < rows.size(); i++) {
						Elements cols = rows.get(i).select("td");
						jotTournaments.add(new Tournament(cols.select("a").first().text(), cols.select("a").first().absUrl("href"), cols.select("[align=center]").first().text(), cols.select("[align=right]").first().text()));
					}
				}
				// Remove duplicates
				for (int i = 0; i < jotTournaments.size(); i++)
					for (int k = 0; k < jotTournaments.size(); k++)
						if (jotTournaments.get(i).getLink().equals(jotTournaments.get(k).getLink()) && i != k) {
							jotTournaments.remove(k);
							k--;
						}
				// Update DB / Remove cached jotTournaments from the queue
				log.debug(jotTournaments.size() + " tournaments scraped from JOT");
				try {
					String query = "INSERT IGNORE INTO tournaments (name, state, link, date) VALUES ";
					ArrayList<String> args = new ArrayList<String>();
					for (Tournament t : jotTournaments) {
						query += "(?,?,?,STR_TO_DATE(?, '%m/%d/%Y')), ";
						args.add(t.getName());
						args.add(t.getState());
						args.add(t.getLink());
						args.add(t.getDate());
					}
					query = query.substring(0, query.lastIndexOf(", "));
					sql.executePreparedStatement(query, args.toArray(new String[args.size()]));
				} catch (SQLException e) {
					e.printStackTrace();
					log.error(e);
					log.fatal("DB could not be updated with JOT tournament info. " + e.getErrorCode());
				}

				log.info(jotTournaments.size() + " tournaments queued from JOT");

			} catch (IOException e) {
				e.printStackTrace();
			}

			// Modules //

//			WorkerPool jotLD = new WorkerPool();
//			workerManager.add(jotLD);
//			moduleManager.newModule(new io.micheal.debaterank.modules.jot.LD(jotTournaments, sql, jotLD, ds));

//			WorkerPool jotPF = new WorkerPool();
//			workerManager.add(jotPF);
//			moduleManager.newModule(new io.micheal.debaterank.modules.jot.PF(jotTournaments, sql, jotPF));

//			WorkerPool jotCX = new WorkerPool();
//			workerManager.add(jotCX);
//			moduleManager.newModule(new io.micheal.debaterank.modules.jot.CX(jotTournaments, sql, jotCX));

			/////////////
			// Tabroom //
			/////////////

			ArrayList<Tournament> tabroomTournaments = null;
			try {
				// Get seasons so we can iterate through all the tournaments
				Document tlist = Jsoup.connect("https://www.tabroom.com/index/results/").get();
				ArrayList<String> years = new ArrayList<String>();
				for (Element select : tlist.select("select[name=year] > option"))
					years.add(select.attr("value"));
				Collections.reverse(years);
				// Get all the tournaments
				tabroomTournaments = new ArrayList<Tournament>();
				ArrayList<Tournament> dbTournaments = new ArrayList<>();
				ResultSet tournamentRS = sql.executeQuery("SELECT name, link, state, date FROM tournaments WHERE link LIKE 'https://www.tabroom.com/index/tourn/results/index.mhtml?tourn_id=%'");
				while (tournamentRS.next()) // TODO: Remove this when not testing
					dbTournaments.add(new Tournament(tournamentRS.getString(1), tournamentRS.getString(2), tournamentRS.getString(3), tournamentRS.getString(4)));
				tabroomTournaments = new ArrayList<>(dbTournaments);
//				for(String year : years) {
//					Document tournamentDoc = Jsoup.connect("https://www.tabroom.com/index/results/")
//						.data("year", year)
//						.post();
//					ArrayList<String> circuits = new ArrayList<String>();
//					for(Element select : tournamentDoc.select("select[name=circuit_id] > option"))
//						circuits.add(select.attr("value"));
//					circuits.remove("43"); // NDT / CEDA
//					circuits.remove("15"); // College invitationals
//					circuits.remove("49"); // Afghan
//					circuits.remove("141"); // Canada
//
//					for(String circuit : circuits) {
//						Document doc = null;
//						int k = 0;
//						while(k < 3) {
//							try {
//								doc = Jsoup.connect("https://www.tabroom.com/index/results/circuit_tourney_portal.mhtml")
//										.timeout(10 * 1000)
//										.data("circuit_id", circuit)
//										.data("year", year)
//										.post();
//								break;
//							}
//							catch(SocketTimeoutException ste) {
//								k++;
//							}
//						}
//						Element table = doc.select("table[id=Stats]").first();
//						Elements rows = table.select("tr");
//						for(int i = 0;i<rows.size();i++) {
//							Elements cols = rows.get(i).select("td");
//							if (cols.size() > 0)
//								tabroomTournaments.add(new Tournament(cols.get(0).text(), cols.get(0).select("a").first().absUrl("href"), null, cols.get(1).text()));
//						}
//					}
//				}

				// Remove duplicates
				for (int i = 0; i < tabroomTournaments.size(); i++)
					for (int k = 0; k < tabroomTournaments.size(); k++)
						if (tabroomTournaments.get(i).getLink().equals(tabroomTournaments.get(k).getLink()) && i != k) {
							tabroomTournaments.remove(k);
							k--;
						}

				// Update DB / Remove cached tournaments from the queue
				log.debug(tabroomTournaments.size() + " tournaments scraped from tabroom");
				try {
					String query = "INSERT IGNORE INTO tournaments (name, state, link, date) VALUES ";
					ArrayList<String> args = new ArrayList<String>();
					for (Tournament t : tabroomTournaments) {
						query += "(?,?,?,STR_TO_DATE(?, '%m/%d/%Y')), ";
						args.add(t.getName());
						args.add(t.getState());
						args.add(t.getLink());
						args.add(t.getDate());
					}
					query = query.substring(0, query.lastIndexOf(", "));
					sql.executePreparedStatement(query, args.toArray(new String[args.size()]));
				} catch (SQLException e) {
					e.printStackTrace();
					log.error(e);
					log.fatal("DB could not be updated with tabroom tournament info. " + e.getErrorCode());
				}

				log.info(tabroomTournaments.size() + " tournaments queued from tabroom");

			} catch (IOException | SQLException e) {
				e.printStackTrace();
				log.error(e);
				log.fatal("Tabroom could not be updated");
			}

			// Modules //

//			WorkerPool tabroomLD = new WorkerPool();
//			workerManager.add(tabroomLD);
//			moduleManager.newModule(new io.micheal.debaterank.modules.tabroom.LD(new SQLHelper(ds.getBds().getConnection()), log, tabroomTournaments, tabroomLD, ds));

			/////////////
			// Execute //
			/////////////

			try {
				workerManager.start();
			} catch (PoolSizeException e) {
			}

			do {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					log.error(e);
					System.exit(1);
				}
			} while (moduleManager.getActiveCount() != 0 || workerManager.getActiveCount() != 0);

			/////////////
			// Schools //
			/////////////

			try {
				ArrayList<Debater> debaters = Main.getDebaters(sql);
				String query = "INSERT IGNORE INTO schools (name, clean) VALUES ";
				ArrayList<Object> args = new ArrayList<Object>();
				for (Debater debater : debaters) {
					query += "(?, ?), ";
					args.add(debater.getSchool().name);
					args.add(SQLHelper.cleanString(debater.getSchool().name));
				}
				if (!query.equals("INSERT IGNORE INTO schools (name, clean) VALUES ")) {
					query = query.substring(0, query.lastIndexOf(", "));
					sql.executePreparedStatement(query, args.toArray());
					log.info("Schools inserted into database.");
				}
			} catch (SQLException sqle) {
				log.error("Couldn't update schools: " + sqle);
			}

			//////////
			// NSDA //
			//////////

			moduleManager = new ModuleManager();
			workerManager = new WorkerPoolManager();

			// Schools //

			WorkerPool schoolsPool = new WorkerPool();
			workerManager.add(schoolsPool);
			moduleManager.newModule(new Schools(sql, log, schoolsPool, ds));

			// Execute //

			try {
				workerManager.start();
			} catch (PoolSizeException e) {
			}

			do {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					log.error(e);
					System.exit(1);
				}
			} while (moduleManager.getActiveCount() != 0 || workerManager.getActiveCount() != 0);

			System.exit(0); // TODO: TEMP

			// Geocoding //

			// Update debaters' schools //

			log.info("Updating debaters' schools");
			try {
				ArrayList<School> schools = Main.getSchools(sql);
				HashMap<String, School> schoolsHM = new HashMap<>();
				for (School school : schools)
					schoolsHM.put(SQLHelper.cleanString(school.name), school);
				String query = "INSERT INTO debaters (school, id) VALUES ";
				ArrayList<Object> args = new ArrayList<Object>();
				for (Debater debater : Main.getDebaters(sql)) {
					Integer id = new Integer(-1);
					if (debater.getSchool().name != null) {
						School school = schoolsHM.get(SQLHelper.cleanString(debater.getSchool().name));
						if (school != null)
							id = school.getID(sql);
					}
					if (id == -1)
						id = debater.getSchool().getID(sql);
					else
						debater.getSchool().setID(id);
					query += "(?, ?), ";
					args.add(id);
					args.add(debater.getID(sql));
				}
				query = query.substring(0, query.lastIndexOf(", "));
				query += " ON DUPLICATE KEY UPDATE school=VALUES(school),id=VALUES(id)";
				sql.executePreparedStatementArgs(query, args.toArray());
			} catch (SQLException sqle) {
				sqle.printStackTrace();
			}
			log.info("Updated debaters' schools.");

			// Update judges' schools //

			log.info("Updating judges' schools");
			try {
				ArrayList<School> schools = Main.getSchools(sql);
				HashMap<String, School> schoolsHM = new HashMap<>();
				for (School school : schools)
					schoolsHM.put(SQLHelper.cleanString(school.name), school);
				String query = "INSERT INTO judges (school, id) VALUES ";
				ArrayList<Object> args = new ArrayList<Object>();
				for (Judge judge : Main.getJudges(sql)) {
					Integer id = new Integer(-1);
					if (judge.getSchool().name != null) {
						School school = schoolsHM.get(SQLHelper.cleanString(judge.getSchool().name));
						if (school != null)
							id = school.getID(sql);
					}
					if (id == -1)
						id = judge.getSchool().getID(sql);
					else
						judge.getSchool().setID(id);
					query += "(?, ?), ";
					args.add(id);
					args.add(judge.getID(sql));
				}
				query = query.substring(0, query.lastIndexOf(", "));
				query += " ON DUPLICATE KEY UPDATE school=VALUES(school),id=VALUES(id)";
				sql.executePreparedStatementArgs(query, args.toArray());
			} catch (SQLException sqle) {
				sqle.printStackTrace();
			}
			log.info("Updated judges' schools.");

			// Update debaters' states //

			// Debaters //

			//JsoupHelper.retryIfTimeout("http://points.speechanddebate.org/points_application/showreport.php?fname=Micheal&lname=Myers&rpt=findstudent", times)

			////////////////
			// TFA Points //
			////////////////

			///////////////////////
			// Judgephilosophies //
			///////////////////////

			///////////////
			// NDCA Wiki //
			///////////////

			// Begin tasks that are not multi-threaded / order dependent

			//////////////
			// Clean-up //
			//////////////

			/////////////////
			// Calculation //
			/////////////////

			// Bids

			// Glicko-2 //

			// LD

			// Establish rating periods by tournaments
		try {
			ArrayList<DateTime> newWeeks = new ArrayList<DateTime>();
			ResultSet orderedTournaments = sql.executeQuery("SELECT date FROM tournaments WHERE date>'2016-07-01 00:00:00.000' ORDER BY date");
			DateTime last = null;
			while(orderedTournaments.next()) {
				DateTime date = new DateTime(orderedTournaments.getDate(1)).withTimeAtStartOfDay();
				if(last == null || date.equals(last) || date.minusDays(1).equals(last))
					last = date;
				else {
					newWeeks.add(date);
					last = date;
				}
			}

			ResultSet debates = sql.executeQuery("SELECT t.date, round, debater, against, decision from ld_rounds ld JOIN tournaments AS t ON ld.tournament=t.id  WHERE tournament IN (SELECT id FROM tournaments WHERE date>'2016-07-01 00:00:00.000') AND NOT debater=against GROUP BY CONCAT(date, \"-\", round, \"-\", LEAST(debater, against), \"-\", GREATEST(debater, against)) HAVING count(*) > 0 ORDER BY t.date, round");
			ArrayList<Rating> debaters = new ArrayList<Rating>();
			int index = 0;
			RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
			RatingPeriodResults results = new RatingPeriodResults();
			while(true) {
				boolean next = false;
				while((next = debates.next()) && !(new DateTime(debates.getDate(1)).withTimeAtStartOfDay().getMillis() > newWeeks.get(index).getMillis())) {
					// Check to see if we have this debater stored
					Rating debater = null, against = null;
					for(Rating rating : debaters) {
						if(rating.getId() == debates.getInt(3))
							debater = rating;
						if(rating.getId() == debates.getInt(4))
							against = rating;
						if(debater != null && against != null)
							break;
					}
					if(debater == null) {
						debater = new Rating(debates.getInt(3), ratingSystem);
						debaters.add(debater);
					}
					if(against == null) {
						against = new Rating(debates.getInt(4), ratingSystem);
						debaters.add(against);
					}

					// Add result
					if(debates.getString(5).equals("1-0"))
						results.addResult(debater, against);
					else
						results.addResult(against, debater);
				}
				index++;
				ratingSystem.updateRatings(results);
				if(!next)
					break;
			}
			debates.close();
			// Sort by ratings
			Collections.sort(debaters, new RatingsComparator());
			ArrayList<Debater> debatersList = Main.getDebaters(sql);
			for(int i = 1;i<=debaters.size();i++) {
				Debater debater = null;
				for(Debater d : debatersList)
					if(d.getID(sql).intValue() == debaters.get(i-1).getId())
						debater = d;
				log.info(i + ". " + debater + " - " + debaters.get(i-1).getRating() + " / " + debaters.get(i-1).getNumberOfResults());
			}

		} catch (SQLException e) {
			log.error(e);
			log.fatal("Could not update debater ratings.");
		}

//			// PF
//			
//			// Establish rating periods by tournaments
//			try {
//				ArrayList<DateTime> newWeeks = new ArrayList<DateTime>();
//				ResultSet orderedTournaments = sql.executeQuery("SELECT date FROM tournaments WHERE date>'2016-07-01 00:00:00.000' ORDER BY date");
//				DateTime last = null;
//				while(orderedTournaments.next()) {
//					DateTime date = new DateTime(orderedTournaments.getDate(1)).withTimeAtStartOfDay();
//					if(last == null || date.equals(last) || date.minusDays(1).equals(last))
//						last = date;
//					else {
//						newWeeks.add(date);
//						last = date;
//					}
//				}
//				
//				ResultSet debates = sql.executeQuery("SELECT t.date, round, team, against, decision from pf_rounds pf JOIN tournaments AS t ON pf.tournament=t.id  WHERE tournament IN (SELECT id FROM tournaments WHERE date>'2016-07-01 00:00:00.000') AND NOT team=against GROUP BY CONCAT(date, \"-\", round, \"-\", LEAST(team, against), \"-\", GREATEST(team, against)) HAVING count(*) > 0 ORDER BY t.date, round");
//				ArrayList<Rating> teams = new ArrayList<Rating>();
//				int index = 0;
//				RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
//				RatingPeriodResults results = new RatingPeriodResults();
//				while(true) {
//					boolean next = false;
//					while((next = debates.next()) && !(new DateTime(debates.getDate(1)).withTimeAtStartOfDay().getMillis() > newWeeks.get(index).getMillis())) {
//						// Check to see if we have this debater stored
//						Rating team = null, against = null;
//						for(Rating rating : teams) {
//							if(rating.getId() == debates.getInt(3))
//								team = rating;
//							if(rating.getId() == debates.getInt(4))
//								against = rating;
//							if(team != null && against != null)
//								break;
//						}
//						if(team == null) {
//							team = new Rating(debates.getInt(3), ratingSystem);
//							teams.add(team);
//						}
//						if(against == null) {
//							against = new Rating(debates.getInt(4), ratingSystem);
//							teams.add(against);
//						}
//						
//						// Add result
//						if(debates.getString(5).equals("1-0"))
//							results.addResult(team, against);
//						else
//							results.addResult(against, team);
//					}
//					index++;
//					ratingSystem.updateRatings(results);
//					if(!next)
//						break;
//				} 
//				debates.close();
//				
//				// Sort by ratings
//				Collections.sort(teams, new RatingsComparator());
//				ArrayList<Team> teamList = DebateHelper.getTeams(sql);
//				for(int i = 1;i<=teams.size();i++) {
//					Team team = null;
//					for(Team t : teamList)
//						if(t.getID().intValue() == teams.get(i-1).getId()) {
//							team = t;
//							break;
//						}
//					Debater debater1 = team.getLeft();
//					Debater debater2 = team.getRight();
//					log.info(i + ". " + debater1.getFirst() + " " + debater1.getLast() + " and " + debater2.getFirst() + " " + debater2.getLast() + " (" + debater2.getSchool() + ") " + " - " + teams.get(i-1).getRating() + " / " + teams.get(i-1).getNumberOfResults());
//				}
//				
//			} catch (SQLException e) {
//				log.error(e);
//				log.fatal("Could not update debater ratings.");
//			}

			// CX

//			// Establish rating periods by tournaments
//			try {
//				ArrayList<DateTime> newWeeks = new ArrayList<DateTime>();
//				ResultSet orderedTournaments = sql.executeQuery("SELECT date FROM tournaments WHERE date>'2016-07-01 00:00:00.000' ORDER BY date");
//				DateTime last = null;
//				while(orderedTournaments.next()) {
//					DateTime date = new DateTime(orderedTournaments.getDate(1)).withTimeAtStartOfDay();
//					if(last == null || date.equals(last) || date.minusDays(1).equals(last))
//						last = date;
//					else {
//						newWeeks.add(date);
//						last = date;
//					}
//				}
//
//				ResultSet debates = sql.executeQuery("SELECT t.date, round, team, against, decision from cx_rounds cx JOIN tournaments AS t ON cx.tournament=t.id  WHERE tournament IN (SELECT id FROM tournaments WHERE date>'2016-07-01 00:00:00.000') AND NOT team=against GROUP BY CONCAT(date, \"-\", round, \"-\", LEAST(team, against), \"-\", GREATEST(team, against)) HAVING count(*) > 0 ORDER BY t.date, round");
//				ArrayList<Rating> teams = new ArrayList<Rating>();
//				int index = 0;
//				RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
//				RatingPeriodResults results = new RatingPeriodResults();
//				while(true) {
//					boolean next = false;
//					while((next = debates.next()) && !(new DateTime(debates.getDate(1)).withTimeAtStartOfDay().getMillis() > newWeeks.get(index).getMillis())) {
//						// Check to see if we have this debater stored
//						Rating team = null, against = null;
//						for(Rating rating : teams) {
//							if(rating.getId() == debates.getInt(3))
//								team = rating;
//							if(rating.getId() == debates.getInt(4))
//								against = rating;
//							if(team != null && against != null)
//								break;
//						}
//						if(team == null) {
//							team = new Rating(debates.getInt(3), ratingSystem);
//							teams.add(team);
//						}
//						if(against == null) {
//							against = new Rating(debates.getInt(4), ratingSystem);
//							teams.add(against);
//						}
//
//						// Add result
//						if(debates.getString(5).equals("1-0"))
//							results.addResult(team, against);
//						else
//							results.addResult(against, team);
//					}
//					index++;
//					ratingSystem.updateRatings(results);
//					if(!next)
//						break;
//				}
//				debates.close();
//
//				// Sort by ratings
//				Collections.sort(teams, new RatingsComparator());
//				ArrayList<Team> teamList = DebateHelper.getTeams(sql);
//				for(int i = 1;i<=teams.size();i++) {
//					Team team = null;
//					for(Team t : teamList)
//						if(t.getID().intValue() == teams.get(i-1).getId()) {
//							team = t;
//							break;
//						}
//					Debater debater1 = team.getLeft();
//					Debater debater2 = team.getRight();
//					log.info(i + ". " + debater1.getFirst() + " " + debater1.getLast() + " and " + debater2.getFirst() + " " + debater2.getLast() + " (" + debater2.getSchool() + ") " + " - " + teams.get(i-1).getRating() + " / " + teams.get(i-1).getNumberOfResults());
//				}
//
//			} catch (SQLException e) {
//				log.error(e);
//				log.fatal("Could not update debater ratings.");
//			}

			System.exit(0); // Temp - 1 loop
		} catch(Exception e) {
			e.printStackTrace();
			log.error(e);
			log.error("Could not execute Main");
		}
	}

}
