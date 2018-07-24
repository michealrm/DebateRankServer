package net.debaterank.server;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.WriteModel;
import net.debaterank.server.models.*;
import net.debaterank.server.modules.ModuleManager;
import net.debaterank.server.modules.PoolSizeException;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.modules.WorkerPoolManager;
import net.debaterank.server.modules.jot.JOTEntryInfo;
import net.debaterank.server.modules.tabroom.TabroomEntryInfo;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mongodb.morphia.*;
import org.mongodb.morphia.query.Query;

import javax.print.Doc;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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

//				// TEMP CALCULATIONS
//
//				try {
//					int aff = 0, neg = 0;
//					ResultSet set = sql.executeQuery("SELECT side, decision FROM ld_rounds JOIN tournaments as t ON t.id=ld_rounds.tournament WHERE t.date>'2016-07-01 00:00:00.000' AND absUrl like '%tabroom%'");
//					while(set.next()) {
//						if(set.getString(1) != null && set.getString(2) != null) {
//							if(set.getString(1).equals("A") && set.getString(2).equals("1-0"))
//								aff++;
//							else if(set.getString(1).equals("A") && set.getString(2).equals("0-1"))
//								neg++;
//							System.out.println("Aff: " + aff);
//							System.out.println("Neg: " + neg);
//						}
//
//
//
//					}
//
//					System.out.println("\nFinal");
//					System.out.println("Aff: " + aff);
//					System.out.println("Neg: " + neg);
//
//				} catch(SQLException e) {}

		/////////
		// JOT //
		/////////

		ArrayList<Tournament> jotTournaments = new ArrayList<>();
		ArrayList<Tournament> tournamentsInDB = new ArrayList<>();
		int jotScraped = 0;
		MongoCollection<org.bson.Document> tournamentCollection = db.getCollection("tournaments");
		for(org.bson.Document doc : tournamentCollection.aggregate(Arrays.asList(Aggregates.project(Projections.include(Arrays.asList("link", "rounds_contains", "rounds_exists")))))) {
			Tournament tournament = new Tournament();
			tournament.setId(doc.getObjectId("_id"));
			tournament.setLink(doc.getString("link"));
			Object roundsContains = null;
			if((roundsContains = doc.get("rounds_contains")) instanceof org.bson.Document) {
				HashMap<String, Boolean> hm = new HashMap<>();
				org.bson.Document rcDoc = (org.bson.Document)roundsContains;
				hm.put("ld", rcDoc.getBoolean("ld"));
				hm.put("pf", rcDoc.getBoolean("pf"));
				hm.put("cx", rcDoc.getBoolean("cx"));
				
				tournament.setRounds_contains(hm);
			}
			else
				log.warn("rounds_contains was not the correct type!");
			Object roundsExists = null;
			if((roundsExists = doc.get("rounds_exists")) instanceof org.bson.Document) {
				HashMap<String, Boolean> hm = new HashMap<>();
				org.bson.Document rcDoc = (org.bson.Document)roundsExists;
				hm.put("ld", rcDoc.getBoolean("ld"));
				hm.put("pf", rcDoc.getBoolean("pf"));
				hm.put("cx", rcDoc.getBoolean("cx"));

				tournament.setRounds_exists(hm);
			}
			else
				log.warn("rounds_exists was not the correct type!");
			tournamentsInDB.add(tournament);
		}
		
		try {
			// Get seasons so we can iterate through all the jotTournaments
			Document tlist = Jsoup.connect("http://www.joyoftournaments.com/results.asp").get();
			ArrayList<String> years = new ArrayList<String>();
			for (Element select : tlist.select("select"))
				if (select.attr("name").equals("season"))
					for (Element option : select.select("option"))
						years.add(option.attr("value"));

			// Get all the tournaments
//			jotTournaments = new ArrayList<Tournament>();
			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
			for (String year : years) {
				Document tournamentDoc = Jsoup.connect("http://www.joyoftournaments.com/results.asp").timeout(10 * 1000)
						.data("state", "")
						.data("month", "0")
						.data("season", year)
						.post();

				Element table = tournamentDoc.select("table.bc").first();
				Elements rows = table.select("tr");
				long lastTime = 0;
				for (int i = 1; i < rows.size(); i++) {
					Elements cols = rows.get(i).select("td");
					try {
						Tournament tournament = new Tournament(cols.select("a").first().text(), cols.select("a").first().absUrl("href"), cols.select("[align=center]").first().text(), formatter.parse(cols.select("[align=right]").first().text()));
						boolean inDB = false;
						for(Tournament t : tournamentsInDB) {
							if(tournament.getLink().equals(t.getLink())) {
								tournament.replaceNull(t);
								inDB = true;
								break;
							}
						}
						if(!inDB) {
							datastore.save(tournament);
						}
						jotTournaments.add(tournament);
						jotScraped++;
						if(System.currentTimeMillis() - lastTime > 5000) {
							lastTime = System.currentTimeMillis();
							log.info("JOT Scraped: " + jotScraped);
						}
					} catch(ParseException pe) {
						log.error("Couldn't insert JOT tournament because we couldn't format the date", pe);
					}
				}
			}

			// Update DB / Remove cached jotTournaments from the queue
			log.info(jotScraped + " tournaments scraped from JOT.");
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Modules //

		ArrayList<JOTEntryInfo> jotTournamentInfosLD = new ArrayList<>();
		ArrayList<JOTEntryInfo> jotTournamentInfosPF = new ArrayList<>();
		ArrayList<JOTEntryInfo> jotTournamentInfosCX = new ArrayList<>();

		WorkerPool jotEntry = new WorkerPool();
		workerManager.add(jotEntry);
		moduleManager.newModule(new net.debaterank.server.modules.jot.JOTEntry(jotTournaments, jotTournamentInfosLD, jotTournamentInfosPF, jotTournamentInfosCX, jotEntry, datastore, db));

		/////////////
		// Tabroom //
		/////////////

		SimpleDateFormat tabroomFormatter = new SimpleDateFormat("MM/dd/yyyy");
		ArrayList<Tournament> tabroomTournaments = null;
		int tabroomScraped = 0;
		try {
			// Get seasons so we can iterate through all the tournaments
			Document tlist = Jsoup.connect("https://www.tabroom.com/index/results/").get();
			ArrayList<String> years = new ArrayList<>();
			for (Element select : tlist.select("select[name=year] > option"))
				years.add(select.attr("value"));
			Collections.reverse(years);
			// Get all the tournaments
			tabroomTournaments = new ArrayList<>();
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
						long lastTime = System.currentTimeMillis();
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
									boolean inDB = false;
									for(Tournament t : tournamentsInDB) {
										if(tournament.getLink().equals(t.getLink())) {
											tournament.replaceNull(t);
											inDB = true;
											break;
										}
									}
									if(!inDB) {
										datastore.save(tournament);
									}
									tabroomTournaments.add(tournament);
									tabroomScraped++;
									if(System.currentTimeMillis() - lastTime > 5000) {
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

			log.info(tabroomScraped + " tournaments scraped from tabroom.");

		} catch (IOException e) {
			e.printStackTrace();
			log.error(e);
			log.fatal("Tabroom could not be updated");
		}

		// Modules //

		ArrayList<TabroomEntryInfo> tabroomTournamentInfosLD = new ArrayList<>();
		ArrayList<TabroomEntryInfo> tabroomTournamentInfosPF = new ArrayList<>();
		ArrayList<TabroomEntryInfo> tabroomTournamentInfosCX = new ArrayList<>();

		WorkerPool tabroomEntry = new WorkerPool();
		workerManager.add(tabroomEntry);
		moduleManager.newModule(new net.debaterank.server.modules.tabroom.TabroomEntry(tabroomTournaments, tabroomTournamentInfosLD, tabroomTournamentInfosPF, tabroomTournamentInfosCX, tabroomEntry, datastore, db));

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

		// Update / save tournaments //
		log.info("Saving JOT tournaments");
		for(Tournament t : jotTournaments)
			datastore.save(t);
		log.info("Saving Tabroom tournaments");
		for(Tournament t : tabroomTournaments)
			datastore.save(t);

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
		log.info("Scraping " + (jotTournamentInfosLD.size() + jotTournamentInfosCX.size() + jotTournamentInfosPF.size()) + " (" + jotTournamentInfosLD.size() + "LD " + jotTournamentInfosPF.size() + "PF " + jotTournamentInfosCX.size() + "CX)");
		log.info("Scraping " + (tabroomTournamentInfosLD.size() + tabroomTournamentInfosCX.size() + tabroomTournamentInfosPF.size()) + " (" + tabroomTournamentInfosLD.size() + "LD " + tabroomTournamentInfosPF.size() + "PF " + tabroomTournamentInfosCX.size() + "CX)");

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
