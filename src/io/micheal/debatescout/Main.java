package io.micheal.debatescout;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.mysql.cj.xdevapi.DbDoc;

public class Main {

	public Log log;
	public boolean active = true;
	private Connection sql;
	private String host, name, user, pass;
	private int port;
	
	public static void main(String[] args) {
		new Main().run();
	}
	
	public Main() {
		log = LogFactory.getLog(Main.class);
		log.debug("Instantiated logger");

		Configurations configs = new Configurations();
		try
		{
		    Configuration config = configs.properties(new File("config.properties"));
		    host = config.getString("db.host");
		    name = config.getString("db.name");
		    user = config.getString("db.user");
		    pass = config.getString("db.pass");
		    port = config.getInt("db.port");
		}
		catch (ConfigurationException cex)
		{
		    log.error(cex);
		    System.exit(0);
		}
		
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			log.error(e);
			System.exit(1);
		}
		
		try {
			sql = DriverManager.getConnection(host, user, pass);
		} catch (SQLException e) {
			log.error(e);
			System.exit(1);
		}
	}
	
	public void run() {
		
		while(active) {
			try {
				Thread.sleep(1); // Change to next update time (defined in config)
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			// JOT Results
			try {
				
				// Get seasons so we can iterate through all the tournaments
				Document tlist = Jsoup.connect("http://www.joyoftournaments.com/results.asp").get();
				ArrayList<String> years = new ArrayList<String>();
				for(Element select : tlist.select("select"))
					if(select.attr("name").equals("season"))
						for(Element option : select.select("option"))
							years.add(option.attr("value"));

				// Get all the tournaments
				ArrayList<Tournament> tournaments = new ArrayList<Tournament>();
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
				
				log.info(tournaments.size() + " tournaments scraped from JOT");
				
				// Scape events per tournament
				
				for(Tournament t : tournaments) {
					Document tPage = Jsoup.connect(t.getLink()).get();
					Elements lds = tPage.select("a[title~=LD|Lincoln|L-D]");
					for(Element ld : lds)
						if(ld != null && ld.text().equals("Prelims")) { // Add Packet & Elims
							Document prelim = Jsoup.connect(ld.absUrl("href")).get();
							Element table = prelim.select("table[border=1]").first();
							Elements rows = table.select("tr:has(table)");
							HashMap<String, Debater> competitors = new HashMap<String, Debater>();
							
							// Register all debaters
							
							for(Element row : rows) {
								Elements infos = row.select("td").first().select("td");
								competitors.put(infos.get(2).text(), new Debater(infos.get(3).text(), infos.get(1).text()));
							}
							
							// Parse rounds
							for(int i = 0;i<rows.size();i++) {
								Elements cols = rows.select("td[width=80]");
								for(int k = 1;k<cols.size()-1;k++) {
									Element speaks = cols.get(k).select("[width=50%][align=left]").first();
									Element side = cols.get(k).select("[width=50%][align=right]").first();
									Element win = cols.get(k).select("[colspan=2].rec").first();
									Element against = cols.get(k).select("[colspan=2][align=right]").first();
									System.out.println(win);
									System.exit(0);
								}
								
							}
						}
				}
				
				System.exit(0);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Searches the SQL tables for the specified name. If no match is found, a debater will be created and returned
	 * @return
	 */
	private int getOrCreateDebaterID(String name, String school) {
		return 0;
	}
}
