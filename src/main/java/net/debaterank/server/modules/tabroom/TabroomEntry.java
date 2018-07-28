package net.debaterank.server.modules.tabroom;

import com.mongodb.client.MongoDatabase;
import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.Module;
import net.debaterank.server.modules.WorkerPool;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.mongodb.morphia.Datastore;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

import static net.debaterank.server.util.NetIOHelper.getInputStream;
import static net.debaterank.server.util.NetIOHelper.readJsonFromInputStream;

// <Tournament, <Tournament ID, Event ID>>
public class TabroomEntry extends Module {

	private ArrayList<Tournament> tournaments;
	private ArrayList<TabroomEntryInfo> ld;
	private ArrayList<TabroomEntryInfo> pf;
	private ArrayList<TabroomEntryInfo> cx;
	private WorkerPool manager;

	public TabroomEntry(ArrayList<Tournament> tournaments, ArrayList<TabroomEntryInfo> ld, ArrayList<TabroomEntryInfo> pf, ArrayList<TabroomEntryInfo> cx, WorkerPool manager, Datastore datastore, MongoDatabase db) {
		super(LogManager.getLogger(net.debaterank.server.modules.tabroom.TabroomEntry.class), datastore, db);
		this.tournaments = tournaments;
		this.ld = ld;
		this.pf = pf;
		this.cx = cx;
		this.manager = manager;
	}

	public void run() {
		// Scrape events per tournament
		for(Tournament t : tournaments) {
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
					for(int i = 0; i < eventsArr.length(); i++) {
						try {
							JSONObject jObject = eventsArr.getJSONObject(i);
							int tourn = jObject.getInt("TOURN");
							int event = jObject.getInt("ID");
							String eventName = jObject.getString("EVENTNAME");
							if(tourn == tourn_id) {
								if (eventName.matches("^.*(LD|Lincoln|L-D).*$")) {
									String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn + "&event_id=" + event + "&output=json";
									log.info("Queuing tabroom LD " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event);
									ld.add(new TabroomEntryInfo(t, tourn_id, event, endpoint));
									ldExists = true;
								}
								if (eventName.matches("^.*(PF|Public|Forum|P-F).*$")) {
									String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn + "&event_id=" + event + "&output=json";
									log.info("Queuing tabroom PF " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event);
									pf.add(new TabroomEntryInfo(t, tourn_id, event, endpoint));
									pfExists = true;
								}
								if (eventName.matches("^.*(CX|Cross|Examination|C-X|Policy).*$")) {
									String endpoint = "https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn + "&event_id=" + event + "&output=json";
									log.info("Queuing tabroom CX " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event);
									cx.add(new TabroomEntryInfo(t, tourn_id, event, endpoint));
									cxExists = true;
								}
							}
						} catch(Exception e1) {}
					}
					if(!ldExists)
						t.putScraped("LD", true);
					if(!pfExists)
						t.putScraped("PF", true);
					if(!cxExists)
						t.putScraped("CX", true);
					iStream.close();


				} catch(Exception e) {
					e.printStackTrace();
				}
			});
		}
	}
}