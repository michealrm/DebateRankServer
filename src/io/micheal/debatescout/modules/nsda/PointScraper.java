package io.micheal.debatescout.modules.nsda;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import io.micheal.debatescout.Debater;
import io.micheal.debatescout.helpers.DebateHelper;
import io.micheal.debatescout.helpers.SQLHelper;
import io.micheal.debatescout.modules.Module;
import io.micheal.debatescout.modules.WorkerPool;

public class PointScraper extends Module {

	private WorkerPool manager;
	
	public PointScraper(SQLHelper sql, WorkerPool manager) {
		super(sql, LogManager.getLogger(PointScraper.class));
		this.manager = manager;
	}

	public void run() {
		try {
			ArrayList<Debater> debaters = DebateHelper.getDebaters(sql);
			for(Debater debater : debaters) {
				manager.newModule(new Runnable() {
					public void run() {
						try {
							ArrayList<Debater> searchDebaters = searchDebater(debater);
						} catch (IOException ioe) {
							log.error(ioe);
							log.log(DebateHelper.NSDA, "Could not update NSDA points for " + debater.getID());
						}
					}
				});
			}
		} catch (SQLException sqle) {
			log.error(sqle);
			log.fatal("Could not update NSDA points - " + sqle.getErrorCode());
		}
	}
	
	public static ArrayList<Debater> searchDebater(Debater debater) throws IOException {
		Document search = Jsoup.connect("http://points.speechanddebate.org/points_application/showreport.php?fname="+ debater.getFirst() + "&lname=" + debater.getLast() + "&rpt=findstudent").get();
		Elements rows = search.select("table.subitem").select("tr:not(.colhead)");
		System.out.println(rows);
		return null;
	}

}
