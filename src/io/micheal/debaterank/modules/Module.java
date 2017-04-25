package io.micheal.debaterank.modules;

import org.apache.logging.log4j.Logger;

import io.micheal.debaterank.helpers.SQLHelper;

public abstract class Module implements Runnable {

	public SQLHelper sql;
	public Logger log;
	
	public Module(SQLHelper sql, Logger log) {
		this.sql = sql;
		this.log = log;
	}
	
	public abstract void run();
	
}
