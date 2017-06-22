package io.micheal.debaterank;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

import io.micheal.debaterank.modules.ModuleManager;
import io.micheal.debaterank.modules.PoolSizeException;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.modules.WorkerPoolManager;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.RatingsComparator;
import io.micheal.debaterank.util.SQLHelper;

public class Main {

	public Logger log;
	public boolean active = true;
	private SQLHelper sql;
	private Configuration config;
	public static HashMap<Debater, Debater> pointers; // From, To
	
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

			sql = new SQLHelper(host, port, name, user, pass);
		} catch (Exception e) {
			log.error(e);
			System.exit(1);
		}
		try {
			pointers = new HashMap<Debater, Debater>();
			ResultSet set = sql.executeQuery("SELECT old_first, old_middle, old_last, old_surname, old_school, first, middle, last, surname, school, `to` FROM pointers p JOIN debaters AS d ON d.id=p.to");
			while(set.next()) {
				Debater one = new Debater(set.getString(1), set.getString(2), set.getString(3), set.getString(4), set.getString(5));
				Debater two = new Debater(set.getString(6), set.getString(7), set.getString(8), set.getString(9), set.getString(10));
				two.setID(set.getInt(11));
				pointers.put(one, two);
			}
		} catch (SQLException e) {}
	}
	
	public void run() {

		while(active) {
			active = false;
			// TODO: Change to next update time
			// TODO: Check if thread pool = 0
			
			///////////////
			// Variables //
			///////////////
			
			ModuleManager moduleManager = new ModuleManager();
			WorkerPoolManager workerManager = new WorkerPoolManager();
			
			/////////
			// JOT //
			/////////
			
			ArrayList<Tournament> jotTournaments = null;
			try {
				// Get seasons so we can iterate through all the jotTournaments
				Document tlist = Jsoup.connect("http://www.joyoftournaments.com/results.asp").get();
				ArrayList<String> years = new ArrayList<String>();
				for(Element select : tlist.select("select"))
					if(select.attr("name").equals("season"))
						for(Element option : select.select("option"))
							years.add(option.attr("value"));

				// Get all the jotTournaments
				jotTournaments = new ArrayList<Tournament>();
				for(String year : years) {
					Document tournamentDoc = Jsoup.connect("http://www.joyoftournaments.com/results.asp")
						.data("state","")
						.data("month", "0")
						.data("season", year)
						.post();
					
					Element table = tournamentDoc.select("table.bc").first();
					Elements rows = table.select("tr");
					for(int i = 1;i<rows.size();i++) {
						Elements cols = rows.get(i).select("td");
						jotTournaments.add(new Tournament(cols.select("a").first().text(), cols.select("a").first().absUrl("href"), cols.select("[align=center]").first().text(), cols.select("[align=right]").first().text()));
					}
				}
				// Remove duplicates
				for(int i = 0;i<jotTournaments.size();i++)
					for(int k = 0;k<jotTournaments.size();k++)
						if(jotTournaments.get(i).getLink().equals(jotTournaments.get(k).getLink()) && i != k) {
							jotTournaments.remove(k);
							k--;
						}
				// Update DB / Remove cached jotTournaments from the queue
				log.debug(jotTournaments.size() + " tournaments scraped from JOT");
				try {
					String query = "INSERT IGNORE INTO tournaments (name, state, link, date) VALUES ";
					ArrayList<String> args = new ArrayList<String>();
					for(Tournament t : jotTournaments) {
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
			
			WorkerPool jotLD = new WorkerPool();
			//workerManager.add(jotLD);
			//moduleManager.newModule(new io.micheal.debaterank.modules.jot.LD(jotTournaments, sql, jotLD));
			
			WorkerPool jotPF = new WorkerPool();
			//workerManager.add(jotPF);
			//moduleManager.newModule(new io.micheal.debaterank.modules.jot.PF(jotTournaments, sql, jotPF));
			
			WorkerPool jotCX = new WorkerPool();
			//workerManager.add(jotCX);
			//moduleManager.newModule(new io.micheal.debaterank.modules.jot.CX(jotTournaments, sql, jotCX));
				
			/////////////
			// Tabroom //
			/////////////
			
			ArrayList<Tournament> tabroomTournaments = null;
			try {
				// Get seasons so we can iterate through all the tournaments
				Document tlist = Jsoup.connect("https://www.tabroom.com/index/results/").get();
				ArrayList<String> years = new ArrayList<String>();
				for(Element select : tlist.select("select[name=year] > option"))
					years.add(select.attr("value"));
				Collections.reverse(years);
				// Get all the tournaments
				tabroomTournaments = new ArrayList<Tournament>();
				for(String year : years) {
					Document tournamentDoc = Jsoup.connect("https://www.tabroom.com/index/results/")
						.data("year", year)
						.post();
					ArrayList<String> circuits = new ArrayList<String>();
//					for(Element select : tournamentDoc.select("select[name=circuit_id] > option"))
//						circuits.add(select.attr("value"));
					circuits.add("6"); // Testing for National Circuit
					for(String circuit : circuits) {
						Document doc = Jsoup.connect("https://www.tabroom.com/index/results/")
								.data("year", year)
								.data("circuit_id", circuit)
								.post();
						Element table = doc.select("table[id=results]").first();
						Elements rows = table.select("tr");
						for(int i = 0;i<rows.size();i++) {
							Elements cols = rows.get(i).select("td");
							if(cols.size() > 0 && cols.get(1).text().endsWith("/US"))
								tabroomTournaments.add(new Tournament(cols.get(3).text(), cols.get(4).select("a").first().absUrl("href"), cols.get(1).text().substring(0,cols.get(1).text().indexOf("/US")), cols.get(0).text()));
						}
					}
				}
				
				// Remove duplicates TODO: Add a better method. This doesn't work I think
				for(int i = 0;i<tabroomTournaments.size();i++)
					for(int k = 0;k<tabroomTournaments.size();k++)
						if(tabroomTournaments.get(i).getLink().equals(tabroomTournaments.get(k).getLink()) && i != k) {
							tabroomTournaments.remove(k);
							k--;
						}
				
				// Update DB / Remove cached tournaments from the queue
				log.debug(tabroomTournaments.size() + " tournaments scraped from tabroom");
				try {
					String query = "INSERT IGNORE INTO tournaments (name, state, link, date) VALUES ";
					ArrayList<String> args = new ArrayList<String>();
					for(Tournament t : tabroomTournaments) {
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
			
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Modules //
			
			WorkerPool tabroomLD = new WorkerPool();
			//workerManager.add(tabroomLD);
			//moduleManager.newModule(new io.micheal.debaterank.modules.tabroom.LD(tabroomTournaments, sql, tabroomLD));
			
			/////////////
			// Execute //
			/////////////
				
			try {
				workerManager.start();
			} catch (PoolSizeException e) {}
			
			do {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					log.error(e);
					System.exit(1);
				}
			} while(moduleManager.getActiveCount() != 0 || workerManager.getActiveCount() != 0);
			
			//////////
			// NSDA //
			//////////
			
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
//				ResultSet debates = sql.executeQuery("SELECT t.date, round, debater, against, decision from ld_rounds ld JOIN tournaments AS t ON ld.tournament=t.id  WHERE tournament IN (SELECT id FROM tournaments WHERE date>'2016-07-01 00:00:00.000') AND NOT debater=against GROUP BY CONCAT(date, \"-\", round, \"-\", LEAST(debater, against), \"-\", GREATEST(debater, against)) HAVING count(*) > 0 ORDER BY t.date, round");
//				ArrayList<Rating> debaters = new ArrayList<Rating>();
//				int index = 0;
//				RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
//				RatingPeriodResults results = new RatingPeriodResults();
//				while(true) {
//					boolean next = false;
//					while((next = debates.next()) && !(new DateTime(debates.getDate(1)).withTimeAtStartOfDay().getMillis() > newWeeks.get(index).getMillis())) {
//						// Check to see if we have this debater stored
//						Rating debater = null, against = null;
//						for(Rating rating : debaters) {
//							if(rating.getId() == debates.getInt(3))
//								debater = rating;
//							if(rating.getId() == debates.getInt(4))
//								against = rating;
//							if(debater != null && against != null)
//								break;
//						}
//						if(debater == null) {
//							debater = new Rating(debates.getInt(3), ratingSystem);
//							debaters.add(debater);
//						}
//						if(against == null) {
//							against = new Rating(debates.getInt(4), ratingSystem);
//							debaters.add(against);
//						}
//						
//						// Add result
//						if(debates.getString(5).equals("1-0"))
//							results.addResult(debater, against);
//						else
//							results.addResult(against, debater);
//					}
//					index++;
//					ratingSystem.updateRatings(results);
//					if(!next)
//						break;
//				} 
//				debates.close();
//				
//				// Sort by ratings
//				Collections.sort(debaters, new RatingsComparator());
//				ArrayList<Debater> debatersList = DebateHelper.getDebaters(sql);
//				for(int i = 1;i<=debaters.size();i++) {
//					Debater debater = null;
//					for(Debater d : debatersList)
//						if(d.getID().intValue() == debaters.get(i-1).getId())
//							debater = d;
//					log.info(i + ". " + debater + " - " + debaters.get(i-1).getRating() + " / " + debaters.get(i-1).getNumberOfResults());
//				}
//				
//			} catch (SQLException e) {
//				log.error(e);
//				log.fatal("Could not update debater ratings.");
//			}
			
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
				
				ResultSet debates = sql.executeQuery("SELECT t.date, round, team, against, decision from cx_rounds cx JOIN tournaments AS t ON cx.tournament=t.id  WHERE tournament IN (SELECT id FROM tournaments WHERE date>'2016-07-01 00:00:00.000') AND NOT team=against GROUP BY CONCAT(date, \"-\", round, \"-\", LEAST(team, against), \"-\", GREATEST(team, against)) HAVING count(*) > 0 ORDER BY t.date, round");
				ArrayList<Rating> teams = new ArrayList<Rating>();
				int index = 0;
				RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
				RatingPeriodResults results = new RatingPeriodResults();
				while(true) {
					boolean next = false;
					while((next = debates.next()) && !(new DateTime(debates.getDate(1)).withTimeAtStartOfDay().getMillis() > newWeeks.get(index).getMillis())) {
						// Check to see if we have this debater stored
						Rating team = null, against = null;
						for(Rating rating : teams) {
							if(rating.getId() == debates.getInt(3))
								team = rating;
							if(rating.getId() == debates.getInt(4))
								against = rating;
							if(team != null && against != null)
								break;
						}
						if(team == null) {
							team = new Rating(debates.getInt(3), ratingSystem);
							teams.add(team);
						}
						if(against == null) {
							against = new Rating(debates.getInt(4), ratingSystem);
							teams.add(against);
						}
						
						// Add result
						if(debates.getString(5).equals("1-0"))
							results.addResult(team, against);
						else
							results.addResult(against, team);
					}
					index++;
					ratingSystem.updateRatings(results);
					if(!next)
						break;
				} 
				debates.close();
				
				// Sort by ratings
				Collections.sort(teams, new RatingsComparator());
				ArrayList<Team> teamList = DebateHelper.getTeams(sql);
				for(int i = 1;i<=teams.size();i++) {
					Team team = null;
					for(Team t : teamList)
						if(t.getID().intValue() == teams.get(i-1).getId()) {
							team = t;
							break;
						}
					Debater debater1 = team.getLeft();
					Debater debater2 = team.getRight();
					log.info(i + ". " + debater1.getFirst() + " " + debater1.getLast() + " and " + debater2.getFirst() + " " + debater2.getLast() + " (" + debater2.getSchool() + ") " + " - " + teams.get(i-1).getRating() + " / " + teams.get(i-1).getNumberOfResults());
				}
				
			} catch (SQLException e) {
				log.error(e);
				log.fatal("Could not update debater ratings.");
			}
			
			//System.exit(0); // Temp - 1 loop
		}
	}
}
