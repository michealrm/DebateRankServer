package io.micheal.debatescout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

public class Main {

	public static Log log;
	public static boolean active = true;
	
	static {
		log = LogFactory.getLog(Main.class);
		log.debug("Instantiated logger");
	}
	
	public static void main(String[] args) {
		
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
							Elements rows = table.select("tr");
							HashMap<String, Debater> competitors = new HashMap<String, Debater>();
							
							// Register all debaters
							
							Elements tds = rows.select("tr ~ td");
							System.out.println(tds);
							
							// Parse rounds
							for(int i = 0;i<rows.size();i++) {
								Elements cols = rows.select("td");
								
								
							}
						}
				}
				
				System.exit(0);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
