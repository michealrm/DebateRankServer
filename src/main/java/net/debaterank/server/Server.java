package net.debaterank.server;

import net.debaterank.server.entities.Tournament;
import net.debaterank.server.modules.ModuleManager;
import net.debaterank.server.modules.PoolSizeException;
import net.debaterank.server.modules.WorkerPoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Server {

	private static Logger log;
	private static Session session;
	private static SessionFactory factory;
	private static Transaction transaction;

	private static void setupHibernate() {
		log.info("Setting up hibernate");
		StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().configure("hibernate.cfg.xml").build();
		Metadata md = new MetadataSources(ssr).getMetadataBuilder().build();

		factory = md.getSessionFactoryBuilder().build();
		session = factory.openSession();
		transaction = session.beginTransaction();
		log.info("Hibernate setup completed");
	}

	public static void main(String[] args) {

		log = LogManager.getLogger(Server.class);
		log.info("Initialized logger");

		setupHibernate();

		///////////////
		// Variables //
		///////////////

		ModuleManager moduleManager = new ModuleManager();
		WorkerPoolManager workerManager = new WorkerPoolManager();

		List<String> scrapedLinks = session.createQuery("select link from Tournament where scraped = true").list();
		ArrayList<Tournament> jotTournaments = new ArrayList<>();
		ArrayList<Tournament> tabroomTournaments = new ArrayList<>();

        int tabroomScraped = 0;
        int jotScraped = 0;

        /////////
		// JOT //
		/////////

        long lastTime = System.currentTimeMillis();
		try {
			// Get seasons so we can iterate through all the jotTournaments
			Document tlist = Jsoup.connect("https://www.joyoftournaments.com/results.asp").get();
			ArrayList<String> years = new ArrayList<>();
			for (Element select : tlist.select("select"))
				if (select.attr("name").equals("season"))
					for (Element option : select.select("option"))
						years.add(option.attr("value"));
			// Get all the tournaments
			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
			for (String year : years) {
				Document tournamentDoc = Jsoup.connect("https://www.joyoftournaments.com/results.asp").timeout(10 * 1000)
						.data("state", "")
						.data("month", "0")
						.data("season", year)
						.post();
				Element table = tournamentDoc.select("table.bc#rlist").first();
				Elements rows = table.select("tr");
				for (int i = 1; i < rows.size(); i++) {
					Elements cols = rows.get(i).select("td");
					try {
						Tournament tournament = new Tournament(cols.select("a").first().text(), cols.select("a").first().absUrl("href"), cols.select("[align=center]").first().text(), formatter.parse(cols.select("[align=right]").first().text()));
						if(!scrapedLinks.contains(tournament.getLink()))
							jotTournaments.add(tournament);
						jotScraped++;
						if(System.currentTimeMillis() - lastTime > 1000) {
							lastTime = System.currentTimeMillis();
							log.info("JOT Scraped: " + jotScraped);
						}
					} catch(Exception pe) {
						log.error("Couldn't insert JOT tournament because we couldn't format the date", pe);
					}
				}
			}

			// Update DB / Remove cached jotTournaments from the queue
            log.info(jotScraped + " tournaments retrieved from JOT. Need to scrape " + jotTournaments.size() + " tournaments from JOT.");
		} catch (IOException e) {
			e.printStackTrace();
		}

		// JOT Modules //


		/////////////
		// Tabroom //
		/////////////

		SimpleDateFormat tabroomFormatter = new SimpleDateFormat("MM/dd/yyyy");
        lastTime = System.currentTimeMillis();
		try {
			// Get seasons so we can iterate through all the tournaments
			Document tlist = Jsoup.connect("https://www.tabroom.com/index/results/").get();
			ArrayList<String> years = new ArrayList<>();
			for (Element select : tlist.select("select[name=year] > option"))
				years.add(select.attr("value"));
			Collections.reverse(years);
			// Get all the tournaments
				for(String year : years) {
//					Document tournamentDoc = Jsoup.connect("https://www.tabroom.com/index/results/")
//						.data("year", year)
//						.post();
					ArrayList<String> circuits = new ArrayList<>();
//					for(Element select : tournamentDoc.select("select[name=circuit_id] > option"))
//						circuits.add(select.attr("value"));
//					circuits.remove("43"); // NDT / CEDA
//					circuits.remove("15"); // College invitationals
//					circuits.remove("49"); // Afghan
//					circuits.remove("141"); // Canada
					circuits.add("6"); // National Circuit (US HS)

					for(String circuit : circuits) {
						Document doc = null;
						int k = 0;
						while(k < 3) {
							try {
								doc = Jsoup.connect("https://www.tabroom.com/index/results/circuit_tourney_portal.mhtml")
										.timeout(10 * 1000)
										.data("circuit_id", circuit)
										.data("year", year)
										.post();
								break;
							}
							catch(SocketTimeoutException ste) {
								k++;
							}
						}
						Element table = doc.select("table[id=Stats]").first();
						Elements rows = table.select("tr");
						for(int i = 0;i<rows.size();i++) {
							try {
								Elements cols = rows.get(i).select("td");
								if (cols.size() > 0) {
									Tournament tournament = new Tournament(cols.get(0).text(), cols.get(0).select("a").first().absUrl("href"), null, tabroomFormatter.parse(cols.get(1).text()));
                                    if(!scrapedLinks.contains(tournament))
                                    	tabroomTournaments.add(tournament);
                                    // TODO: Add DB check here because tabroom tournament scraping is costly
                                    tabroomScraped++;
                                    if(System.currentTimeMillis() - lastTime > 1000) {
                                        lastTime = System.currentTimeMillis();
                                        log.info("Tabroom Scraped: " + tabroomScraped);
                                    }
								}
							} catch(ParseException pe) {
								log.warn("Couldn't parse the date. Skipping this row.");
							}
						}
					}
				}

			log.info(tabroomScraped + "  tournaments retrieved from Tabroom. Need to scrape " + tabroomTournaments.size() + " tournaments from Tabroom.");

		} catch (IOException e) {
			e.printStackTrace();
			log.error(e);
			log.fatal("Tabroom could not be updated");
		}

        log.info("Saving new tournaments into the DB");
        transaction.commit();
        log.info("Saved tournaments");

		// Tabroom Modules //

		/////////////
		// Execute //
		/////////////

		try {
			workerManager.start();
		} catch (PoolSizeException e) {}

		do {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				log.error(e);
				System.exit(1);
			}
		} while (moduleManager.getActiveCount() != 0 || workerManager.getActiveCount() != 0);

		// Update DB
        log.info("Finished queuing - saving tournaments.");
		if (transaction.getStatus().equals(TransactionStatus.ACTIVE)) {
			transaction.commit();
		}
        log.info("Saved tournaments");

        ////////////////////////
		// Tournament parsing //
		////////////////////////

		// JOT

		// Tabroom

		// Execute //

		log.info("Executing tournament parsing.");
		try {
			workerManager.start();
		} catch (PoolSizeException e) {}

		do {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				log.error(e);
				System.exit(1);
			}
		} while (moduleManager.getActiveCount() != 0 || workerManager.getActiveCount() != 0);
	}

}
