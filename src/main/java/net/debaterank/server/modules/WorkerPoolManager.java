package net.debaterank.server.modules;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.util.ArrayList;

public class WorkerPoolManager {

	public static final int POOL_LENGTH;
	
	private ArrayList<WorkerPool> managers;
	
	static {
		Configurations configs = new Configurations();
		Configuration config = null;
		try {
			config = configs.properties(new File("config.properties"));
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}
		POOL_LENGTH = config.getInt("pool");
	}
	
	public WorkerPoolManager() {
		managers = new ArrayList<>();
	}
	
	public void add(WorkerPool manager) {
		managers.add(manager);
	}

	public WorkerPool newPool() {
		WorkerPool pool = new WorkerPool();
		managers.add(pool);
		return pool;
	}
	
	public void start() throws PoolSizeException {
		int count = managers.size() == 0 ? 0 : (int)Math.ceil((double)POOL_LENGTH / managers.size());
		if(count == 0)
			throw new PoolSizeException(count, POOL_LENGTH);
		for(WorkerPool manager : managers)
			manager.start(count);
	}
	
	public int getActiveCount() {
		int active = 0;
		for(WorkerPool pool : managers)
			active += pool.getActiveCount();
		return active;
	}
	
}
