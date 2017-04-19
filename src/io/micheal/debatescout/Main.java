package io.micheal.debatescout;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.micheal.debatescout.modules.Module;
import io.micheal.debatescout.modules.ModuleManager;
import io.micheal.debatescout.modules.jot.LD;

public class Main {

	public Log log;
	public boolean active = true;
	private SQLHelper sql;
	private Configuration config;
	
	public static void main(String[] args) {
		new Main().run();
	}
	
	public Main() {
		log = LogFactory.getLog(Main.class);
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

			sql = new SQLHelper(log, host, port, name, user, pass);
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
			
			ModuleManager manager = new ModuleManager();
			ArrayList<Module> modules = new ArrayList<Module>();
			
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
					String[] argsArr = new String[args.size()];
					for(int i = 0;i<args.size();i++)
						argsArr[i] = args.get(i);
					sql.executePreparedStatement(query, argsArr);
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
				
			modules.add(new LD(tournaments, sql, log, manager));
			// TODO: Policy
			// TODO: PF
				
			/////////////
			// Tabroom //
			/////////////
			
			//////////
			// NSDA //
			//////////
			
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
				
			for(Module module : modules)
				manager.newModule(module);
				
			/////////////////
			// CALCULATION //
			/////////////////
			
			// Bids
				
			// Glicko-2
//			while(manager.getActiveCount() != 0) {
//				try {
//					Thread.sleep(5000);
//				} catch (InterruptedException e) {
//					log.error(e);
//					System.exit(1);
//				}
//			}
//			
//			HashMap<String, Rating> debaters = new HashMap<String, Rating>();
//			ResultSet orderedT = null;
//			try {
//				orderedT = sql.executeQuery("SELECT (id, date) FROM tournaments ORDERBY date DESC");
//			} catch (SQLException e) {
//				log.error(e);
//				log.fatal("Could not update debater ratings.");
//			}
			
			//System.exit(0); // Temp - 1 loop
		}
	}
}
