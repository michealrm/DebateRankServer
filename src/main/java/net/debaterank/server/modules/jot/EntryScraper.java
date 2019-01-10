package net.debaterank.server.modules.jot;

import net.debaterank.server.entities.Tournament;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.HibernateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class EntryScraper implements Runnable {
	private Logger log;
	private ArrayList<Tournament> tournaments;
	private ArrayList<EntryInfo> tInfo;
	private WorkerPool manager;
	private AtomicInteger counter;
	private AtomicInteger noEventRows;

	public EntryScraper(ArrayList<Tournament> tournaments, ArrayList<EntryInfo> tInfo, WorkerPool manager) {
		log = LogManager.getLogger(EntryScraper.class);
		this.tournaments = tournaments;
		this.tInfo = tInfo;
		this.manager = manager;
	}

	public void run() {
		// Scrape events per tournament
		counter = new AtomicInteger(1);
		noEventRows = new AtomicInteger(0);
		Session session = HibernateUtil.getSession();
		Transaction transaction = session.beginTransaction();
		for(Tournament t : tournaments) {
			if(!t.isScraped())
				manager.newModule(() -> {
					try {
						Document tPage = Jsoup.connect(t.getLink()).timeout(10 * 1000).get();
						Elements ldEventRows = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)), tr:has(td:has(nobr:matches(LD|Lincoln|L-D)))");
						Elements pfEventRows = tPage.select("tr:has(td:matches(PF|Public|Forum|P-F)), tr:has(td:has(nobr:matches(PF|Public|Forum|P-F)))");
						Elements cxEventRows = tPage.select("tr:has(td:matches(CX|Cross|Examination|C-X|Policy)), tr:has(td:has(nobr:matches(CX|Cross|Examination|C-X|Policy)))");

						tInfo.add(new EntryInfo(t, ldEventRows, pfEventRows, cxEventRows));

						String events = "(";
						if(!ldEventRows.isEmpty()) events += "LD ";
						if(!pfEventRows.isEmpty()) events += "PF ";
						if(!cxEventRows.isEmpty()) events += "CX ";
						if(events.charAt(events.length() - 1) == ' ') events = events.substring(0, events.length() - 1);
						events += ")";

						if(ldEventRows.isEmpty() && pfEventRows.isEmpty() && cxEventRows.isEmpty()) {
							log.info(t.getName() + " contains no event rows (" + noEventRows.incrementAndGet() + "). Setting scraped = true and skipping.\t[" + counter.incrementAndGet() + " / " + tournaments.size() + "]");
							t.setScraped(true);
							session.merge(t);
						}
						else {
							log.info("Added \"" + t.getName() + "\"'s entry info " + events + "\t[" + counter.getAndIncrement() + " / " + tournaments.size() + "]");
						}
					} catch(Exception e) {
						log.error(e);
						e.printStackTrace();
						log.fatal("Could not update " + t.getName());
						counter.incrementAndGet();
					}
				});
		}

		int lastCounter;
		while(counter.get() < tournaments.size()) {
			try {
				lastCounter = counter.get();
				Thread.sleep(5000);
				if(lastCounter == counter.get())
					break;
			} catch (InterruptedException e) {
				log.fatal("Entry scraper thread was interrupted. Attempting to save the tournaments");
				transaction.commit();
				return;
			}
		}
		log.info("Saving scraped tournaments in entry scraper");
		transaction.commit();
		session.close();
		log.info("Saved");
	}
}
