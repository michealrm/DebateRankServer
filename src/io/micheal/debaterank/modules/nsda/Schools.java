package io.micheal.debaterank.modules.nsda;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.School;
import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.SQLHelper;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class Schools extends Module {

	private WorkerPool manager;
	private ArrayList<School> schools;


	public Schools(SQLHelper sql, Logger log, WorkerPool manager) {
		super(sql, log);
		this.manager = manager;
		schools = new ArrayList<School>();
	}

	public void run() {
		try {
			ArrayList<Debater> debaters = DebateHelper.getDebaters(sql);
			HashSet<String> schoolNames = new HashSet<String>();
			for(Debater debater : debaters)
				if(debater.getSchool() != null && !schoolNames.contains(debater.getSchool() + " | " + debater.getSchool().replaceAll("[,'\".]", "")))
					schoolNames.add(debater.getSchool() + "|" + debater.getSchool().replaceAll("[,'\".]", ""));
			for(String str : schoolNames) {
				manager.newModule(new Runnable() {
					public void run() {
						try {
							String string = str.split("\\|")[0];
							String clean = str.split("\\|")[1];
							Document schoolPage = Jsoup.connect("https://points.speechanddebate.org/points_application/showreport.php?name=" + clean.replaceAll(" ", "%20") + "&state=&rpt=findschool").timeout(10 * 1000).get();
							Elements elements = schoolPage.select("div[align=left]");
							if (elements == null || elements.first() == null)
								return;
							String link = schoolPage.select("a").eq(1).get(0).absUrl("href");
							String address = elements.first().textNodes().get(2).text();
							String state = address.split(", ")[1];
							School school = new School();
							school.name = string;
							school.clean = clean;
							school.link = link;
							school.address = address;
							school.state = state;
							schools.add(school);
							System.out.println("tick");
						} catch(IOException e) {}
					}
				});
			}

			boolean running = true;
			while(running) {
				try {
					Thread.sleep(5000);
				} catch(InterruptedException e) {
					return;
				}
				running = manager.getActiveCount() != 0;
			}

			for(School school : schools)
				System.out.print(school.address + ";");
			System.out.println();
			for(School school : schools)
				System.out.print(school.name + ";");
		} catch(SQLException e) {
			log.error("Couldn't update NSDA schools: " + e);
		}
	}

}
