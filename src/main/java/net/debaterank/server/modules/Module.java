package net.debaterank.server.modules;

import com.mongodb.client.MongoDatabase;
import org.apache.log4j.Logger;
import org.mongodb.morphia.Datastore;

public abstract class Module implements Runnable {

	public Logger log;
	public Datastore datastore;
	public MongoDatabase db;
	
	public Module(Logger log, Datastore datastore, MongoDatabase db) {
		this.log = log;
		this.datastore = datastore;
		this.db = db;
	}
	
	public abstract void run();
	
}
