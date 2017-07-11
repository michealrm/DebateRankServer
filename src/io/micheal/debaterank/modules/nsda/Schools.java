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
	private List<School> schools;


	public Schools(SQLHelper sql, Logger log, WorkerPool manager) {
		super(sql, log);
		this.manager = manager;
		schools = Collections.synchronizedList(new ArrayList<School>());
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
							String address = elements.first().textNodes().get(2).text().trim();
							String state = address.split(", ")[1];
							School school = new School();
							school.name = string;
							school.clean = clean;
							school.link = link;
							school.address = address;
							school.state = state;
							schools.add(school);
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

			try {
				String query = "INSERT INTO schools (name, clean, nsda_link, address, state) VALUES "; // Don't need to add judges school since every jduge is from a school where there is a debater
				ArrayList<Object> args = new ArrayList<Object>();
				for(School school : schools) {
					try {
						ArrayList<Object> a = new ArrayList<Object>();
						a.add(school.name);
						a.add(SQLHelper.cleanString(school.name));
						a.add(school.link);
						if(school.state.length() > 2) {
							a.add(null);
							a.add(null);
						}
						else {
							a.add(school.address);
							a.add(school.state);
						}
						query += "(?, ?, ?, ?, ?), ";
						args.addAll(a);
					} catch(NullPointerException npe) {
						continue;
					}
				}

				if (!query.equals("INSERT INTO schools (name, clean, nsda_link, address, state) VALUES ")) {
					query = query.substring(0, query.lastIndexOf(", "));
					query += " ON DUPLICATE KEY UPDATE name=VALUES(name), clean=VALUES(clean), nsda_link=VALUES(nsda_link), address=VALUES(address), state=VALUES(state)";
					sql.executePreparedStatement(query, args.toArray());
					log.log(DebateHelper.NSDA, "NSDA schools inserted into database.");
				}
			} catch (SQLException sqle) {
				log.error("Could not update NSDA schools - " + sqle.getErrorCode());
				sqle.printStackTrace();
			}
		} catch(SQLException e) {
			log.error("Couldn't update NSDA schools: " + e);
		}
	}

}
