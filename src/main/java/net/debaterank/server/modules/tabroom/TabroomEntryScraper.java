package net.debaterank.server.modules.tabroom;

import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.WorkerPool;
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

import static net.debaterank.server.util.DRHelper.readJsonArrayFromInputStream;
import static net.debaterank.server.util.DRHelper.readJsonObjectFromInputStream;
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
		counter = new AtomicInteger(1);
		Session eiSession = HibernateUtil.getSession();
		Transaction eiTransaction = eiSession.beginTransaction();
		for(Tournament t : tournaments) {
			if(entryInfoDataExists(dir, t)) {
				EntryInfo<EntryInfo.TabroomEventInfo> ei = getFromFile(dir, t);
				if(ei != null) {
					tInfo.add(ei);
					eiSession.merge(t);
					counter.incrementAndGet();
					continue;
				}
			}
			manager.newModule(() -> {

				Session session = HibernateUtil.getSession();
				Transaction transaction = session.beginTransaction();
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
						tournIDStr.append(t.getLink().toCharArray()[index]);
						++index;
					}

					int tourn_id = Integer.parseInt(tournIDStr.toString());

					DateTime joda = new DateTime(t.getDate());
					URL url = new URL("https://www.tabroom.com/api/current_tournaments.mhtml?timestring=" + joda.getYear() + "-" + String.format("%02d", joda.getMonthOfYear()) + "-" + String.format("%02d", joda.getDayOfMonth()) + "T12:00:00&output=json");

					InputStream iStream = url.openStream();

					JSONArray jsonArray = null;
					try {
						jsonArray = readJsonArrayFromInputStream(iStream);
					} catch(Exception e) {
						log.error(String.format("Skipping %s (URL: %s). This is not a valid JSON object.", t.getLink(), url));
						return;
					}
					boolean ldExists = false;
					boolean pfExists = false;
					boolean cxExists = false;
					EntryInfo<EntryInfo.TabroomEventInfo> entryInfo = new EntryInfo<>(t);
					StringBuilder sb = new StringBuilder();
					sb.append("TID");
					sb.append(tourn_id);
					sb.append(" ");
					for(int i = 0; i < jsonArray.length(); i++) {
						try {
							JSONObject tournObject = jsonArray.getJSONObject(i);
							int id = tournObject.getInt("id");
							if(tourn_id != id)
								continue;
							JSONArray categoriesArr = tournObject.getJSONArray("categories");
							for(int k = 0; k < categoriesArr.length(); k++) {
								try {
									JSONArray eventsArr = categoriesArr.getJSONObject(k).getJSONArray("events");
									for (int j = 0; j < eventsArr.length(); j++) {
										JSONObject eventObject = eventsArr.getJSONObject(j);
										if (!eventObject.getString("type").equals("debate"))
											continue;
										int eventId = eventObject.getInt("id");
										String eventName = eventObject.getString("name");
										if (eventName.matches("^.*(LD|Lincoln|L-D).*$")) {
											String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + eventId + "&output=json";
											sb.append("LD");
											sb.append(eventId);
											sb.append(" ");
											entryInfo.addLdEventRow(new EntryInfo.TabroomEventInfo(tourn_id, eventId, endpoint));
											ldExists = true;
										} else if (eventName.matches("^.*(PF|Public Forum|P-F).*$")) {
											String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + eventId + "&output=json";
											sb.append("PF");
											sb.append(eventId);
											sb.append(" ");
											entryInfo.addPfEventRow(new EntryInfo.TabroomEventInfo(tourn_id, eventId, endpoint));
											pfExists = true;
										}
										// eventName.matches("^.*(CX|Cross|Examination|C-X|Policy|Open|JV|Novice).*$")
										else if (!eventName.matches("^.*(Communication Analysis|Impromptu|Informative|Interp|IPDA|Persuasion|POI|Prose|Speech|LD|Lincoln|L-D|PF|Public Forum|P-F).*$")) {
											String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + eventId + "&output=json";
											sb.append("CX");
											sb.append(eventId);
											sb.append(" ");
											entryInfo.addCxEventRow(new EntryInfo.TabroomEventInfo(tourn_id, eventId, endpoint));
											cxExists = true;
										} else {
											log.fatal(t.getLink() + " Error - unassigned event: " + eventName);
										}
									}
								} catch(Exception e2) {
									log.error(String.format("Error on %s (URL: %s). %s", t.getLink(), url, e2));
								}
							}
						} catch(Exception e1) {
							log.error(String.format("Error on %s (URL: %s). %s", t.getLink(), url, e1));
						}
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
					log.info(tourn_id + " " + t.getName() + " queued and wrote [" + counter.incrementAndGet() + " / " + tournaments.size() + "]");
					log.info(sb.toString());
					iStream.close();
					transaction.commit();
				} catch(Exception e) {
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
		log.info("Saving scraped tournaments in Tabroom entry scraper");
		eiTransaction.commit(); // might not commit anything?
		eiSession.close();
		log.info("Saved");
	}
}