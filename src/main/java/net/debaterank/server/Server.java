package net.debaterank.server;

import net.debaterank.server.models.Debater;
import net.debaterank.server.models.LDRound;
import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.Glicko2;
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

        ///////////////
        // Variables //
        ///////////////

        log.info("Debaters in cache: " + CacheManager.ALL_CACHE_MANAGERS.get(0).getCache("net.debaterank.server.models.Debater").getSize());
        HibernateUtil.loadCache();
        log.info("Debaters in cache: " + CacheManager.ALL_CACHE_MANAGERS.get(0).getCache("net.debaterank.server.models.Debater").getSize());

        ModuleManager moduleManager = new ModuleManager();
        WorkerPoolManager workerManager = new WorkerPoolManager();

        //////////////
        // Glicko-2 //
        //////////////

        moduleManager.newModule(new Glicko2(Glicko2.DebateType.LD, workerManager.newPool()));
        moduleManager.newModule(new Glicko2(Glicko2.DebateType.PF, workerManager.newPool()));
        moduleManager.newModule(new Glicko2(Glicko2.DebateType.CX, workerManager.newPool()));

        execute("first ratings update", workerManager, moduleManager);

        /////////////////////////
        // Tournament scraping //
        /////////////////////////

        HashSet<String> existingLinks = new HashSet<>(session.createQuery("select link from Tournament").list());
        ArrayList<Tournament> jotTournaments = new ArrayList<>();
        ArrayList<Tournament> tabroomTournaments = new ArrayList<>();

        int tabroomScraped = 0;
        int jotScraped = 0;

        // JOT

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
                        if (!existingLinks.contains(link)) {
                            jotTournaments.add(tournament);
                            existingLinks.add(link);
                        }
                        jotScraped++;
                        if (System.currentTimeMillis() - lastTime > 1000) {
                            lastTime = System.currentTimeMillis();
                            log.info("JOT Scraped: " + jotScraped);
                        }
                    } catch (Exception pe) {
                        log.error("Couldn't insert JOT tournament because we couldn't format the date", pe);
                    }
                }
            }

            // Update DB / Remove cached jotTournaments from the queue
            log.info(jotScraped + " tournaments retrieved from JOT. Need to add " + jotTournaments.size() + " to the database");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Tabroom

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
            for (String year : years) {
//					Document tournamentDoc = Jsoup.connect("https://www.tabroom.com/index/results/")
//						.data("year", year)
//						.post();
//					ArrayList<String> circuits = new ArrayList<>();
//					for(Element select : tournamentDoc.select("select[name=circuit_id] > option"))
//						circuits.add(select.attr("value"));
//					circuits = ConfigUtil.getCircuits(circuits);
//					log.info(year + " using circuits: " + circuits);

                for (String circuit : circuits) {
                    Document doc = null;
                    int k = 0;
                    while (k < 3) {
                        try {
                            doc = Jsoup.connect("https://www.tabroom.com/index/results/circuit_tourney_portal.mhtml")
                                    .timeout(10 * 1000)
                                    .data("circuit_id", circuit)
                                    .data("year", year)
                                    .post();
                            break;
                        } catch (SocketTimeoutException ste) {
                            k++;
                        }
                    }
                    Element table = doc.select("table[id=Stats]").first();
                    Elements rows = table.select("tr");
                    for (int i = 0; i < rows.size(); i++) {
                        try {
                            Elements cols = rows.get(i).select("td");
                            if (cols.size() > 0) {
                                Tournament tournament = new Tournament(cols.get(0).text(), cols.get(0).select("a").first().absUrl("href"), null, tabroomFormatter.parse(cols.get(1).text()));
                                if (!existingLinks.contains(tournament.getLink()) && tournament.getDate().before(new Date())) // Will make sure we don't scrape tournaments happening in the future
                                    tabroomTournaments.add(tournament);
                                tabroomScraped++;
                                if (System.currentTimeMillis() - lastTime > 1000) {
                                    lastTime = System.currentTimeMillis();
                                    log.info("Tabroom Scraped: " + tabroomScraped);
                                }
                            }
                        } catch (ParseException pe) {
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
        for (Tournament t : jotTournaments) {
            session.persist(t);
        }
        for (Tournament t : tabroomTournaments) {
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

        moduleManager.newModule(new Glicko2(Glicko2.DebateType.LD, workerManager.newPool()));
        moduleManager.newModule(new Glicko2(Glicko2.DebateType.PF, workerManager.newPool()));
        moduleManager.newModule(new Glicko2(Glicko2.DebateType.CX, workerManager.newPool()));

        execute("second ratings update", workerManager, moduleManager);

        session = HibernateUtil.getSession();
        transaction = session.beginTransaction();

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
