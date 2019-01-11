package net.debaterank.server.modules.jot;

import com.sun.org.apache.xml.internal.security.utils.Base64;
import net.debaterank.server.entities.Tournament;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.HibernateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class EntryScraper implements Runnable {

	public static final String dir = "data/jot_entry/";

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
			if(t.isScraped()) continue;
			if(entryInfoDataExists(t)) {
				EntryInfo ei = getFromFile(t);
				if(ei != null) {
					tInfo.add(ei);
					counter.incrementAndGet();
					continue;
				}
			}
			manager.newModule(() -> {
				try {
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

					if(ldEventRows.isEmpty() && pfEventRows.isEmpty() && cxEventRows.isEmpty()) {
						log.info(t.getName() + " contains no event rows (" + noEventRows.incrementAndGet() + "). Setting scraped = true and skipping.\t[" + counter.incrementAndGet() + " / " + tournaments.size() + "]");
						t.setScraped(true);
						session.merge(t);
					}
					else {
						EntryInfo entryInfo = new EntryInfo(t);
						for(Element eventRow : ldEventRows) {
							String p = null;
							String d = null;
							String b = null;
							Element pE = eventRow.select("a[title]:contains(Prelims)").first();
							Element dE = eventRow.select("a[title]:contains(Double Octos)").first();
							Element bE = eventRow.select("a[title]:contains(Bracket)").first();
							if(pE != null)
								p = pE.absUrl("href");
							if(dE != null)
								d = dE.absUrl("href");
							if(bE != null)
								b = bE.absUrl("href");
							if(p == null && d == null && b == null) {
								continue;
							}
							EntryInfo.EventLinks links = new EntryInfo.EventLinks(p, d, b);
							log.info(links);
							entryInfo.addLdEventRow(links);
						}
						tInfo.add(entryInfo);
						writeToFile(entryInfo);
						log.info("Queued and wrote \"" + t.getName() + "\"'s entry info " + events + "\t[" + counter.getAndIncrement() + " / " + tournaments.size() + "]");
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

	private boolean entryInfoDataExists(Tournament t) {
		return new File(getFileName(t)).exists();
	}

	private EntryInfo getFromFile(Tournament t) {
		FileInputStream fis = null;
		ObjectInputStream ois = null;
		try {
			String fileName = getFileName(t);
			fis = new FileInputStream(fileName);
			ois = new ObjectInputStream(fis);
			Object o = ois.readObject();
			if(o instanceof EntryInfo) {
				EntryInfo entryInfo = (EntryInfo) o;
				entryInfo.setTournament(t); // Tournament is transient
				log.info(t.getName() + " entry data retrieved from file");
				return entryInfo;
			}
		} catch(IOException | ClassNotFoundException e) {}
		finally {
			try {
				fis.close();
				ois.close();
			} catch(Exception e) {}
		}
		log.info("File retrieval failed for " + t.getName());
		return null;
	}

	private void writeToFile(EntryInfo entryInfo) throws IOException {
		File dirFile = new File(dir);
		if(!dirFile.exists())
			dirFile.mkdirs();
		String fileName = getFileName(entryInfo.getTournament());
		FileOutputStream fos = new FileOutputStream(fileName);
		ObjectOutputStream oos = new ObjectOutputStream(fos);

		oos.writeObject(entryInfo);
		oos.close();
		fos.close();
		log.info(entryInfo.getTournament().getName() + " entry info written to file");
	}

	private String getFileName(Tournament t) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch(NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		byte[] hash = md.digest(Base64.encode(t.getName().getBytes()).getBytes());
		StringBuilder sb = new StringBuilder();
		for(byte b : hash)
			sb.append(String.format("%02x", b));
		return dir + sb.toString() + ".dat";
	}

}
