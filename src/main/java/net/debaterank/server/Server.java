package net.debaterank.server;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import net.debaterank.server.models.DebaterPointer;
import net.debaterank.server.models.JudgePointer;
import net.debaterank.server.models.School;
import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.ModuleManager;
import net.debaterank.server.modules.PoolSizeException;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.modules.WorkerPoolManager;
import net.debaterank.server.modules.jot.JOTEntry;
import net.debaterank.server.modules.jot.JOTEntryInfo;
import net.debaterank.server.modules.tabroom.TabroomEntry;
import net.debaterank.server.modules.tabroom.TabroomEntryInfo;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.query.Query;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class Server {

	private Logger log;
	private MongoClient mongoClient;
	private MongoDatabase db;
	public static List<DebaterPointer> debaterPointers;
	public static List<JudgePointer> judgePointers;
	private final Morphia morphia = new Morphia();
	private final Datastore datastore;
	private CodecRegistry pojoCodecRegistry;
	public static HashMap<String, School> schoolStore = new HashMap<>();

	public Server() {
		log = LogManager.getLogger(Server.class);
		log.info("Instantiated logger");

		morphia.mapPackage("net.debaterank.server.models");

		Configurations configs = new Configurations();
		try
		{
			Configuration config = configs.properties(new File("mongo.properties"));
			String connectionString = config.getString("connectionString");
			pojoCodecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build()));
			MongoClientOptions.Builder optsWithCodecs = MongoClientOptions.builder(new MongoClientURI(connectionString).getOptions()).codecRegistry(pojoCodecRegistry);
			MongoClientURI uri = new MongoClientURI(connectionString, optsWithCodecs);
			mongoClient = new MongoClient(uri);
			db = mongoClient.getDatabase("debaterank");
		} catch (Exception e) {
			log.error(e);
			System.exit(1);
		}

		// pointers
		datastore = morphia.createDatastore(mongoClient, "debaterank");
		datastore.ensureIndexes();

		final Query<DebaterPointer> query = datastore.createQuery(DebaterPointer.class);
		debaterPointers = query.asList();

		final Query<JudgePointer> query2 = datastore.createQuery(JudgePointer.class);
		judgePointers = query2.asList();

	}

	public void run() {

		///////////////
		// Variables //
		///////////////

		ModuleManager moduleManager = new ModuleManager();
		WorkerPoolManager workerManager = new WorkerPoolManager();

        ArrayList<Tournament> jotTournaments = new ArrayList<>();
        ArrayList<Tournament> tournamentsInDB = new ArrayList<>(datastore.createQuery(Tournament.class).asList());
        ArrayList<Tournament> possibleJOT = new ArrayList<>();
        ArrayList<Tournament> notInDB = new ArrayList<>();
        ArrayList<Tournament> possibleTabroom = new ArrayList<>();
        ArrayList<Tournament> tabroomTournaments = new ArrayList<>();

        int tabroomScraped = 0;
        int jotScraped = 0;

        for(Tournament t : tournamentsInDB)
            if (t.getLink().contains("joyoftournaments"))
                possibleJOT.add(t);
            else if(t.getLink().contains("tabroom"))
                possibleTabroom.add(t);

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
						boolean scraped = false;
						boolean inDB = false;
						for(Tournament t : possibleJOT) {
							if(tournament.getLink().equals(t.getLink())) {
								tournament.replaceNull(t);
								scraped = tournament.isScraped("LD") && tournament.isScraped("PF") && tournament.isScraped("CX");
								inDB = true;
								break;
							}
						}
						if(!scraped)
                            jotTournaments.add(tournament);
						if(!inDB)
						    notInDB.add(tournament);
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

		// Modules //

		ArrayList<JOTEntryInfo> jotTournamentInfosLD = new ArrayList<>();
		ArrayList<JOTEntryInfo> jotTournamentInfosPF = new ArrayList<>();
		ArrayList<JOTEntryInfo> jotTournamentInfosCX = new ArrayList<>();

		WorkerPool jotEntry = new WorkerPool();
		workerManager.add(jotEntry);
		moduleManager.newModule(new JOTEntry(jotTournaments, jotTournamentInfosLD, jotTournamentInfosPF, jotTournamentInfosCX, jotEntry, datastore, db));

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
                                    boolean scraped = false;
                                    boolean inDB = false;
                                    for(Tournament t : possibleTabroom) {
                                        if(tournament.getLink().equals(t.getLink())) {
                                            tournament.replaceNull(t);
                                            scraped = tournament.isScraped("LD") && tournament.isScraped("PF") && tournament.isScraped("CX");
                                            inDB = true;
                                            break;
                                        }
                                    }
                                    if(!scraped)
                                        tabroomTournaments.add(tournament);
                                    if(!inDB)
                                        notInDB.add(tournament);
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

        log.info("Saving " + notInDB.size() + " tournaments in the DB");
        datastore.save(notInDB);
        log.info("Saved tournaments");

		// Modules //

		ArrayList<TabroomEntryInfo> tabroomTournamentInfosLD = new ArrayList<>();
		ArrayList<TabroomEntryInfo> tabroomTournamentInfosPF = new ArrayList<>();
		ArrayList<TabroomEntryInfo> tabroomTournamentInfosCX = new ArrayList<>();

		WorkerPool tabroomEntry = new WorkerPool();
		workerManager.add(tabroomEntry);
		moduleManager.newModule(new TabroomEntry(tabroomTournaments, tabroomTournamentInfosLD, tabroomTournamentInfosPF, tabroomTournamentInfosCX, tabroomEntry, datastore, db));

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
        datastore.save(jotTournaments);
        datastore.save(tabroomTournaments);
        log.info("Saved tournaments");

        ////////////////////////
		// Tournament parsing //
		////////////////////////

		// JOT
		WorkerPool jotLD = new WorkerPool();
		workerManager.add(jotLD);
		moduleManager.newModule(new net.debaterank.server.modules.jot.LD(jotTournamentInfosLD, jotLD, datastore, db));

		WorkerPool jotPF = new WorkerPool();
		workerManager.add(jotPF);
		moduleManager.newModule(new net.debaterank.server.modules.jot.PF(jotTournamentInfosPF, jotPF, datastore, db));

		WorkerPool jotCX = new WorkerPool();
		workerManager.add(jotCX);
		moduleManager.newModule(new net.debaterank.server.modules.jot.CX(jotTournamentInfosCX, jotCX, datastore, db));

		// Tabroom
		WorkerPool tabroomLD = new WorkerPool();
		workerManager.add(tabroomLD);
		moduleManager.newModule(new net.debaterank.server.modules.tabroom.LD(tabroomTournamentInfosLD, tabroomLD, datastore, db));

		WorkerPool tabroomPF = new WorkerPool();
		workerManager.add(tabroomPF);
		moduleManager.newModule(new net.debaterank.server.modules.tabroom.PF(tabroomTournamentInfosPF, tabroomPF, datastore, db));

		WorkerPool tabroomCX = new WorkerPool();
		workerManager.add(tabroomCX);
		moduleManager.newModule(new net.debaterank.server.modules.tabroom.CX(tabroomTournamentInfosCX, tabroomCX, datastore, db));

		// Execute //

		log.info("Executing tournament parsing.");
		log.info("JOT Scraping " + (jotTournamentInfosLD.size() + jotTournamentInfosCX.size() + jotTournamentInfosPF.size()) + " (" + jotTournamentInfosLD.size() + "LD " + jotTournamentInfosPF.size() + "PF " + jotTournamentInfosCX.size() + "CX)");
		log.info("Tabroom Scraping " + (tabroomTournamentInfosLD.size() + tabroomTournamentInfosCX.size() + tabroomTournamentInfosPF.size()) + " (" + tabroomTournamentInfosLD.size() + "LD " + tabroomTournamentInfosPF.size() + "PF " + tabroomTournamentInfosCX.size() + "CX)");

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

	public static void main(String[] args) {
		new Server().run();
	}

}
