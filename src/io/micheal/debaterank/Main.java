package io.micheal.debaterank;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

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
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.modules.WorkerPoolManager;
import io.micheal.debaterank.modules.jot.LD;
import io.micheal.debaterank.util.RatingsComparator;
import io.micheal.debaterank.util.SQLHelper;

public class Main {

	public Logger log;
	public boolean active = true;
	private SQLHelper sql;
	private Configuration config;
	
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
			
			ArrayList<Tournament> tournaments = null;
			try {
				// Get seasons so we can iterate through all the tournaments
				Document tlist = Jsoup.connect("http://www.joyoftournaments.com/results.asp").get();
				ArrayList<String> years = new ArrayList<String>();
				for(Element select : tlist.select("select"))
					if(select.attr("name").equals("season"))
						for(Element option : select.select("option"))
							years.add(option.attr("value"));

				// Get all the tournaments
				tournaments = new ArrayList<Tournament>();
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
						tournaments.add(new Tournament(cols.select("a").first().text(), cols.select("a").first().absUrl("href"), cols.select("[align=center]").first().text(), cols.select("[align=right]").first().text()));
					}
				}
				
				// Update DB / Remove cached tournaments from the queue
				log.debug(tournaments.size() + " tournaments scraped from JOT");
				try {
					String query = "INSERT IGNORE INTO tournaments (name, state, link, date) VALUES ";
					ArrayList<String> args = new ArrayList<String>();
					for(Tournament t : tournaments) {
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
				
				// Remove duplicates
				for(int i = 0;i<tournaments.size();i++)
					for(int k = 0;k<tournaments.size();k++)
						if(tournaments.get(i).getLink().equals(tournaments.get(k).getLink()) && i != k) {
							tournaments.remove(k);
							i--;
							k--;
						}
				
				log.info(tournaments.size() + " tournaments queued from JOT");
			
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Modules //
			
			WorkerPool ld = new WorkerPool();
			workerManager.add(ld);
			moduleManager.newModule(new LD(tournaments, sql, ld));
			// TODO: Policy
			// TODO: PF
				
			/////////////
			// Tabroom //
			/////////////
			
			while(moduleManager.getActiveCount() != 0) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					log.error(e);
					System.exit(1);
				}
			}
			
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
			
			/////////////
			// Execute //
			/////////////
				
//			try {
//				workerManager.start();
//			} catch (PoolSizeException e) {
//				log.error(e);
//				log.fatal("Not enough threads!");
//				System.exit(1);
//			}
			
			// Begin tasks that are not multi-threaded / order dependent
			
			//////////////
			// Clean-up //
			//////////////
			
			/////////////////
			// Calculation //
			/////////////////
			
			// Bids
				
			// Glicko-2
			
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
					System.out.println("Updated results");
					if(!next)
						break;
				} 
				debates.close();

				// Sort by ratings
				Collections.sort(debaters, new RatingsComparator());
				for(int i = 1;i<=100;i++)
					System.out.println(i + ". " + debaters.get(i-1));
//				for(int i = 1;i<=100;i++)
//					System.out.println(i + ". " + debaters.get(i));
				
			} catch (SQLException e) {
				log.error(e);
				log.fatal("Could not update debater ratings.");
			}
			
			//System.exit(0); // Temp - 1 loop
		}
	}
}
