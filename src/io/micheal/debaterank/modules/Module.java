package io.micheal.debaterank.modules;

import io.micheal.debaterank.util.SQLHelper;
import org.apache.logging.log4j.Logger;

public abstract class Module implements Runnable {

	public SQLHelper sql;
	public Logger log;
	
	public Module(SQLHelper sql, Logger log) {
		this.sql = sql;
		this.log = log;
	}
	
	public abstract void run();
	
}
