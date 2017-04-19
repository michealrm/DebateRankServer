package io.micheal.debatescout.modules;

import org.apache.commons.logging.Log;

import io.micheal.debatescout.helpers.SQLHelper;

public abstract class Module implements Runnable {

	public SQLHelper sql;
	public Log log;
	
	public Module(SQLHelper sql, Log log) {
		this.sql = sql;
		this.log = log;
	}
	
	public abstract void run();
	
}
