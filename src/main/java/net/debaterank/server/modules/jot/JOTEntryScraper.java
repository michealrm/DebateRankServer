package net.debaterank.server.modules.jot;

import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.EntryInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static net.debaterank.server.util.EntryInfo.*;

public class JOTEntryScraper implements Runnable {

	public static final String dir = "data/jot_entry/";

	private Logger log;
	private ArrayList<Tournament> tournaments;
	private ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tInfo;
	private WorkerPool manager;
	private AtomicInteger counter;
	private AtomicInteger noEventRows;

	public JOTEntryScraper(ArrayList<Tournament> tournaments, ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tInfo, WorkerPool manager) {
		log = LogManager.getLogger(JOTEntryScraper.class);
		this.tournaments = tournaments;
		this.tInfo = tInfo;
		this.manager = manager;
	}

	public void run() {
		// Scrape events per tournament
		counter = new AtomicInteger(1);
		noEventRows = new AtomicInteger(0);
		Session eiSession = HibernateUtil.getSession();
		Transaction eiTransaction = eiSession.beginTransaction();
		for(Tournament t : tournaments) {
			if(t.isLdScraped() && t.isPfScraped() && t.isCxScraped()) continue;
			if(entryInfoDataExists(dir, t)) {
				EntryInfo<EntryInfo.JOTEventLinks> ei = getFromFile(dir, t);
				if(ei != null) {
					tInfo.add(ei);
					eiSession.merge(t);
					counter.incrementAndGet();
					continue;
				}
			}
			manager.newModule(() -> {
				Session session = HibernateUtil.getSession();
				try {
					Transaction transaction = session.beginTransaction();
					Document tPage = Jsoup.connect(t.getLink()).timeout(10 * 1000).get();
					Elements ldEventRows = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)), tr:has(td:has(nobr:matches(LD|Lincoln|L-D)))");
					Elements pfEventRows = tPage.select("tr:has(td:matches(PF|Public|Forum|P-F)), tr:has(td:has(nobr:matches(PF|Public|Forum|P-F)))");
					Elements cxEventRows = tPage.select("tr:has(td:matches(CX|Cross|Examination|C-X|Policy)), tr:has(td:has(nobr:matches(CX|Cross|Examination|C-X|Policy)))");

					String events = "(";
					if(!ldEventRows.isEmpty()) events += "LD ";
					if(!pfEventRows.isEmpty()) events += "PF ";
					if(!cxEventRows.isEmpty()) events += "CX ";
					if(events.charAt(events.length() - 1) == ' ') events = events.substring(0, events.length() - 1);
					events += ")";

					EntryInfo entryInfo = new EntryInfo(t);
					if(ldEventRows.isEmpty())
						t.setLdScraped(true);
					else
						getLinks(entryInfo.getLdEventRows(), ldEventRows);
					if(pfEventRows.isEmpty())
						t.setPfScraped(true);
					else
						getLinks(entryInfo.getPfEventRows(), pfEventRows);
					if(cxEventRows.isEmpty())
						t.setCxScraped(true);
					else
						getLinks(entryInfo.getCxEventRows(), cxEventRows);
					if(ldEventRows.isEmpty() || pfEventRows.isEmpty() || cxEventRows.isEmpty())
						session.merge(t);
					tInfo.add(entryInfo);
					writeToFile(dir, entryInfo);
					transaction.commit();
					log.info("Queued and wrote \"" + t.getName() + "\"'s entry info " + events + "\t[" + counter.getAndIncrement() + " / " + tournaments.size() + "]");
				} catch(Exception e) {
					log.error(e);
					e.printStackTrace();
					log.fatal("Could not update " + t.getName());
					counter.incrementAndGet();
				} finally {
					session.close();
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
				log.fatal("Entry scraper thread was interrupted");
				return;
			}
		}
		log.info("Saving scraped tournaments in JOT entry scraper");
		eiTransaction.commit();
		eiSession.close();
		log.info("Saved");
	}

	private ArrayList<EntryInfo.JOTEventLinks> getLinks(ArrayList<EntryInfo.JOTEventLinks> links, Elements eventRows) {
		for(Element eventRow : eventRows) {
			String p = null;
			String d = null;
			String b = null;
			Element pE = eventRow.select("a[title]:contains(Prelims), a:contains(Prelims)").first();
			Element dE = eventRow.select("a[title]:contains(Double Octos), a:contains(Double Octos)").first();
			Element bE = eventRow.select("a[title]:contains(Bracket), a:contains(Bracket)").first();
			if(pE != null)
				p = pE.absUrl("href");
			if(dE != null)
				d = dE.absUrl("href");
			if(bE != null)
				b = bE.absUrl("href");
			if(p == null && d == null && b == null) {
				continue;
			}
			links.add(new EntryInfo.JOTEventLinks(p, d, b));
		}
		return links;
	}

}
