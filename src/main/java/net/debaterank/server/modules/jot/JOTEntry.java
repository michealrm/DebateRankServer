package net.debaterank.server.modules.jot;

import com.mongodb.client.MongoDatabase;
import net.debaterank.server.entities.Tournament;
import net.debaterank.server.modules.Module;
import net.debaterank.server.modules.WorkerPool;
import org.apache.logging.log4j.LogManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.mongodb.morphia.Datastore;

import java.util.ArrayList;

// Heavy Net IO in this Entry module
public class JOTEntry extends Module {

	private ArrayList<Tournament> tournaments;
	private ArrayList<JOTEntryInfo> ld;
	private ArrayList<JOTEntryInfo> pf;
	private ArrayList<JOTEntryInfo> cx;
	private WorkerPool manager;

	public JOTEntry(ArrayList<Tournament> tournaments, ArrayList<JOTEntryInfo> ld, ArrayList<JOTEntryInfo> pf, ArrayList<JOTEntryInfo> cx, WorkerPool manager, Datastore datastore, MongoDatabase db) {
		super(LogManager.getLogger(net.debaterank.server.modules.jot.JOTEntry.class), datastore, db);
		this.tournaments = tournaments;
		this.ld = ld;
		this.pf = pf;
		this.cx = cx;
		this.manager = manager;
	}

	public void run() {
		// Scrape events per tournament
		for(Tournament t : tournaments) {
			if(!(t.isScraped("LD") && t.isScraped("PF") && t.isScraped("CX")))
			manager.newModule(() -> {
				try {
					Document tPage = Jsoup.connect(t.getLink()).timeout(10 * 1000).get();
					Elements ldEventRows = tPage.select("tr:has(td:matches(LD|Lincoln|L-D)), tr:has(td:has(nobr:matches(LD|Lincoln|L-D)))");
					Elements pfEventRows = tPage.select("tr:has(td:matches(PF|Public|Forum|P-F)), tr:has(td:has(nobr:matches(PF|Public|Forum|P-F)))");
					Elements cxEventRows = tPage.select("tr:has(td:matches(CX|Cross|Examination|C-X|Policy)), tr:has(td:has(nobr:matches(CX|Cross|Examination|C-X|Policy)))");

					if (ldEventRows.size() != 0) {
						JOTEntryInfo info = new JOTEntryInfo(t, ldEventRows);
						ld.add(info);
						log.info("Queuing JOT LD " + t.getName());
					} else {
						t.putScraped("LD", true);
					}
					if (pfEventRows.size() != 0) {
						JOTEntryInfo info = new JOTEntryInfo(t, pfEventRows);
						pf.add(info);
						log.info("Queuing JOT PF " + t.getName());
					} else {
						t.putScraped("PF", true);
					}
					if (cxEventRows.size() != 0) {
						JOTEntryInfo info = new JOTEntryInfo(t, cxEventRows);
						cx.add(info);
						log.info("Queuing JOT CX " + t.getName());
					} else {
						t.putScraped("CX", true);
					}
				} catch(Exception e) {
					log.error(e);
					e.printStackTrace();
					log.fatal("Could not update " + t.getName());
				}
			});
		}
	}
}