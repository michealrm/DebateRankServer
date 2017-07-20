package io.micheal.debaterank.modules;

import io.micheal.debaterank.util.DataSource;
import io.micheal.debaterank.util.SQLHelper;
import org.apache.logging.log4j.Logger;

public abstract class Module implements Runnable {

	public SQLHelper sql;
	public Logger log;
	public DataSource ds;
	
	public Module(SQLHelper sql, Logger log, DataSource ds) {
		this.sql = sql;
		this.log = log;
		this.ds = ds;
	}
	
	public abstract void run();
	
}
