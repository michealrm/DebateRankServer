package io.micheal.debatescout;

import org.apache.commons.logging.Log;

public abstract class Module implements Runnable {

	public SQLHelper sql;
	public Log log;
	
	public Module(SQLHelper sql, Log log) {
		this.sql = sql;
		this.log = log;
	}
	
	public abstract void run();
	
}
