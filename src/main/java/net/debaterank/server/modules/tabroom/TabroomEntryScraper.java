package net.debaterank.server.modules.tabroom;

import net.debaterank.server.entities.Tournament;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.modules.jot.JOTEntryScraper;
import net.debaterank.server.util.EntryInfo;
import net.debaterank.server.util.HibernateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static net.debaterank.server.util.NetIOHelper.readJsonFromInputStream;
import static net.debaterank.server.util.EntryInfo.*;

// <Tournament, <Tournament ID, Event ID>>
public class TabroomEntryScraper implements Runnable {

	public static final String dir = "data/tabroom_entry/";

	private Logger log;
	private ArrayList<Tournament> tournaments;
	private ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tInfo;
	private WorkerPool manager;
	private AtomicInteger counter;

	public TabroomEntryScraper(ArrayList<Tournament> tournaments, ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tInfo, WorkerPool manager) {
		log = LogManager.getLogger(TabroomEntryScraper.class);
		this.tournaments = tournaments;
		this.tInfo = tInfo;
		this.manager = manager;
	}


	public void run() {
		// Scrape events per tournament
		Session session = HibernateUtil.getSession();
		Transaction transaction = session.beginTransaction();
		counter = new AtomicInteger(1);
		for(Tournament t : tournaments) {
			if(entryInfoDataExists(dir, t)) {
				EntryInfo<EntryInfo.TabroomEventInfo> ei = getFromFile(dir, t);
				if(ei != null) {
					tInfo.add(ei);
					session.merge(t);
					counter.incrementAndGet();
					continue;
				}
			}
			manager.newModule(() -> {
				try {
					StringBuilder tournIDStr = new StringBuilder();
					int index = t.getLink().indexOf("tourn_id=") + 9;
					while(index < t.getLink().length()) {
						try {
							Integer.parseInt(Character.toString(t.getLink().toCharArray()[index]));
						}
						catch(NumberFormatException e) {
							break;
						}
						tournIDStr.append(Character.toString(t.getLink().toCharArray()[index]));
						++index;
					}

					int tourn_id = Integer.parseInt(tournIDStr.toString());

					DateTime joda = new DateTime(t.getDate());
					URL url = new URL("https://www.tabroom.com/api/current_tournaments.mhtml?timestring=" + joda.getYear() + "-" + String.format("%02d", joda.getMonthOfYear()) + "-" + String.format("%02d", joda.getDayOfMonth()) + "T12:00:00&output=json");

					InputStream iStream = url.openStream();

					JSONObject jsonObject = null;
					try {
						jsonObject = readJsonFromInputStream(iStream);
					} catch(Exception e) {
						log.warn("Skipping " + t.getLink() + " with date " + t.getDate() + ". This is not a valid JSON object. URL: " + url);
						return;
					}
					JSONArray eventsArr = jsonObject.getJSONArray("EVENT");
					boolean ldExists = false;
					boolean pfExists = false;
					boolean cxExists = false;
					EntryInfo<EntryInfo.TabroomEventInfo> entryInfo = new EntryInfo<>(t);
					StringBuilder sb = new StringBuilder();
					sb.append("TID");
					sb.append(tourn_id);
					sb.append(" ");
					for(int i = 0; i < eventsArr.length(); i++) {
						try {
							JSONObject jObject = eventsArr.getJSONObject(i);
							int tourn = jObject.getInt("TOURN");
							int event = jObject.getInt("ID");
							String eventName = jObject.getString("EVENTNAME");
							if(tourn == tourn_id) {
								if (eventName.matches("^.*(LD|Lincoln|L-D).*$")) {
									String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn + "&event_id=" + event + "&output=json";
									sb.append("LD");
									sb.append(event);
									sb.append(" ");
									entryInfo.addLdEventRow(new EntryInfo.TabroomEventInfo(tourn_id, event, endpoint));
									ldExists = true;
								}
								if (eventName.matches("^.*(PF|Public|Forum|P-F).*$")) {
									String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn + "&event_id=" + event + "&output=json";
									sb.append("PF");
									sb.append(event);
									sb.append(" ");
									entryInfo.addPfEventRow(new EntryInfo.TabroomEventInfo(tourn_id, event, endpoint));
									pfExists = true;
								}
								if (eventName.matches("^.*(CX|Cross|Examination|C-X|Policy).*$")) {
									String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn + "&event_id=" + event + "&output=json";
									sb.append("CX");
									sb.append(event);
									sb.append(" ");
									entryInfo.addCxEventRow(new EntryInfo.TabroomEventInfo(tourn_id, event, endpoint));
									cxExists = true;
								}
							}
						} catch(Exception e1) {}
					}
					if(!ldExists)
						t.setLdScraped(true);
					if(!pfExists)
						t.setPfScraped(true);
					if(!cxExists)
						t.setCxScraped(true);
					if(!ldExists || !pfExists || !cxExists)
						session.merge(t);
					writeToFile(dir, entryInfo);
					tInfo.add(entryInfo);
					log.info(tourn_id + " " + t.getName() + " queued and wrote (" + counter.incrementAndGet() + " / " + tournaments.size() + ")");
					log.info(sb.toString());
					counter.incrementAndGet();
					iStream.close();
				} catch(Exception e) {
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
		log.info("Saving scraped tournaments in Tabroom entry scraper");
		transaction.commit();
		session.close();
		log.info("Saved");
	}
}