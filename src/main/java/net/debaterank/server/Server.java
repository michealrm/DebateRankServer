package net.debaterank.server;

import net.debaterank.server.models.Debater;
import net.debaterank.server.models.LDRound;
import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.ModuleManager;
import net.debaterank.server.modules.PoolSizeException;
import net.debaterank.server.modules.WorkerPoolManager;
import net.debaterank.server.modules.tabroom.TabroomEntryScraper;
import net.debaterank.server.util.ConfigUtil;
import net.debaterank.server.util.EntryInfo;
import net.debaterank.server.modules.jot.JOTEntryScraper;
import net.debaterank.server.util.HibernateUtil;
import net.sf.ehcache.CacheManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.goochjs.glicko2.Rating;
import org.goochjs.glicko2.RatingCalculator;
import org.goochjs.glicko2.RatingPeriodResults;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Server {

	private static Logger log;

	public static void main(String[] args) {

		log = LogManager.getLogger(Server.class);
		log.info("Initialized logger");

		Session session = HibernateUtil.getSession();
		Transaction transaction = session.beginTransaction();


		session = HibernateUtil.getSession();
		transaction = session.beginTransaction();

		List<String> seasons = new ArrayList<>();
		List<Double> seasonsResult = session.createSQLQuery("SELECT DISTINCT EXTRACT(year FROM date) FROM tournament " +
				"ORDER BY EXTRACT(year FROM date)").list();
		for(Double d : seasonsResult)
			seasons.add(String.valueOf(d.intValue()));

		for(String season : seasons) {
			String end = String.valueOf(Integer.parseInt(season) + 1);
			List<Object[]> debates = session.createSQLQuery("SELECT ld.id,tournament_id, a_id, n_id, " +
					"string_agg(b.decision, ',') FROM LDRound ld JOIN Tournament AS t ON t.id=ld.tournament_id JOIN " +
					"ldballot AS b ON ld.id=b.round_id WHERE tournament_id IN (SELECT id FROM Tournament WHERE " +
					"date>='" + season + "-07-01 00:00:00.000' AND date<'" + end + "-07-01 00:00:00.000') AND NOT a_id=n_id AND bye=false AND aAfter=0 GROUP BY t.date," +
					"tournament_id,round,a_id,n_id,ld.id ORDER BY t.date, round")
					.list();

			HashMap<String, Rating> ratings = new HashMap<>();
			for(Rating r : Rating.getRatings(season))
				ratings.put(r.getUid(), r);

			RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
			RatingPeriodResults results = new RatingPeriodResults();

			for (Object[] d : debates) {
				Rating aff = null, neg = null;
				aff = ratings.get(String.valueOf(d[2]));
				neg = ratings.get(String.valueOf(d[3]));

				if (aff == null) {
					aff = new Rating(String.valueOf(d[2]), ratingSystem, season);
					ratings.put(aff.getUid(), aff);
				}
				if (neg == null) {
					neg = new Rating(String.valueOf(d[3]), ratingSystem, season);
					ratings.put(neg.getUid(), neg);
				}

				int affBallots = 0;
				int totalBallots = 0;
				for (String s : String.valueOf(d[4]).split(",")) {
					if (s.equals("Aff"))
						affBallots++;
					totalBallots++;
				}
				double affWinPercentage = (double) affBallots / totalBallots;

				if (affWinPercentage > .5)
					results.addResult(aff, neg);
				else
					results.addResult(neg, aff);
				double aBefore = aff.getRating();
				double nBefore = neg.getRating();
				ratingSystem.updateRatings(results);
				double aAfter = aff.getRating();
				double nAfter = neg.getRating();

				LDRound round = (LDRound) session.createQuery("from LDRound where id = :i")
						.setParameter("i", ((BigInteger) d[0]).longValue())
						.getSingleResult();
				round.setaBefore(aBefore);
				round.setnBefore(nBefore);
				round.setaAfter(aAfter);
				round.setnAfter(nAfter);
				session.saveOrUpdate(round);
			}
			for(Rating r : ratings.values())
				session.saveOrUpdate(r);
		}
		transaction.commit();


		transaction = session.beginTransaction();

		///////////////
		// Variables //
		///////////////

		log.info("Debaters in cache: " + CacheManager.ALL_CACHE_MANAGERS.get(0).getCache("net.debaterank.server.models.Debater").getSize());
		HibernateUtil.loadCache();
		log.info("Debaters in cache: " + CacheManager.ALL_CACHE_MANAGERS.get(0).getCache("net.debaterank.server.models.Debater").getSize());

		ModuleManager moduleManager = new ModuleManager();
		WorkerPoolManager workerManager = new WorkerPoolManager();

		HashSet<String> existingLinks = new HashSet<>(session.createQuery("select link from Tournament").list());
		ArrayList<Tournament> jotTournaments = new ArrayList<>();
		ArrayList<Tournament> tabroomTournaments = new ArrayList<>();

        int tabroomScraped = 0;
        int jotScraped = 0;

        /////////
		// JOT //
		/////////

		long lastTime = System.currentTimeMillis() + 1000; // offset by 1s
		try {
			// Get seasons so we can iterate through all the jotTournaments
			Document tlist = Jsoup.connect("https://www.joyoftournaments.com/results.asp").get();
			ArrayList<String> years = new ArrayList<>();
			for (Element select : tlist.select("select"))
				if (select.attr("name").equals("season"))
					for (Element option : select.select("option"))
						years.add(option.attr("value"));
			// Get all the tournaments
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
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
						String name = cols.select("a").first().text();
						String link = cols.select("a").first().absUrl("href");
						String state = cols.select("[align=center]").first().text();
						Date date = formatter.parse(cols.select("[align=right]").first().text());
						Tournament tournament = new Tournament(name, link, state, date);
						if(!existingLinks.contains(link)) {
							jotTournaments.add(tournament);
							existingLinks.add(link);
						}
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
            log.info(jotScraped + " tournaments retrieved from JOT. Need to add " + jotTournaments.size() + " to the database");
		} catch (IOException e) {
			e.printStackTrace();
		}

		/////////////
		// Tabroom //
		/////////////

		SimpleDateFormat tabroomFormatter = new SimpleDateFormat("MM/dd/yyyy");
        lastTime = System.currentTimeMillis() + 1000; // offset by 1s
		ArrayList<String> circuits = ConfigUtil.getCircuits();
		log.info("Tabroom using circuits: " + circuits);
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
//					ArrayList<String> circuits = new ArrayList<>();
//					for(Element select : tournamentDoc.select("select[name=circuit_id] > option"))
//						circuits.add(select.attr("value"));
//					circuits = ConfigUtil.getCircuits(circuits);
//					log.info(year + " using circuits: " + circuits);

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
                                    if(!existingLinks.contains(tournament.getLink()) && tournament.getDate().before(new Date())) // Will make sure we don't scrape tournaments happening in the future
                                    	tabroomTournaments.add(tournament);
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

			log.info(tabroomScraped + " tournaments retrieved from Tabroom. Need to add " + tabroomTournaments.size() + " to the database");

		} catch (IOException e) {
			e.printStackTrace();
			log.error(e);
			log.fatal("Tabroom could not be updated");
		}

		ArrayList<Tournament> jotInDB = new ArrayList<>(session.createQuery("select t from Tournament t where (ldscraped is false or pfscraped is false or cxscraped is false) and link like 'http://www.joyoftournaments.com/%'").list());
		ArrayList<Tournament> tabroomInDB = new ArrayList<>(session.createQuery("select t from Tournament t where (ldscraped is false or pfscraped is false or cxscraped is false) and link like 'https://www.tabroom.com/%'").list());
        log.info("Saving tournaments into the DB");
		for(Tournament t : jotTournaments) {
			session.persist(t);
		}
		for(Tournament t : tabroomTournaments) {
			session.persist(t);
		}
		transaction.commit();
		log.info("Saved " + jotTournaments.size() + " JOT tournaments and " + tabroomTournaments.size() + " tabroom tournaments");
		jotTournaments.addAll(jotInDB);
		tabroomTournaments.addAll(tabroomInDB);
		log.info("JOT tournaments in queue: " + jotTournaments.size());
		log.info("Tabroom tournaments in queue: " + tabroomTournaments.size());

        log.info("Closing tournament session");
        session.close();

		////////////////
		// Entry info //
		////////////////

		// JOT
		ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> jotEntries = new ArrayList<>();
		moduleManager.newModule(new JOTEntryScraper(jotTournaments, jotEntries, workerManager.newPool()));

		//Tabroom
		ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tabroomEntries = new ArrayList<>();
		moduleManager.newModule(new TabroomEntryScraper(tabroomTournaments, tabroomEntries, workerManager.newPool()));

		// Execute
		execute("entry info", workerManager, moduleManager);

        ////////////////////////
		// Tournament parsing //
		////////////////////////

		ConfigUtil.addModules(moduleManager, workerManager, jotEntries, tabroomEntries);

		// Execute //
		execute("tournament parsing", workerManager, moduleManager);

		//////////////
		// Glicko-2 //
		//////////////


		// no multithreading because they have to be processed in order
		// to calcuate rating to rating

		// group by tournament, round
		// join round, ballot, tournament
		// select a, n, ballots

		workerManager.shutdown();
		moduleManager.shutdown();
	}

	public static void execute(String taskName, WorkerPoolManager workerManager, ModuleManager moduleManager) {
		log.info("Executing " + taskName);
		long startTime = System.currentTimeMillis();
		try {
			workerManager.start();
		} catch (PoolSizeException e) {
			log.fatal(e);
		}

		do {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				log.error(e);
				System.exit(1);
			}
		} while (moduleManager.getActiveCount() != 0 || workerManager.getActiveCount() != 0);
		workerManager.clear();
		log.info("Finished executing " + taskName + " in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
	}

}
