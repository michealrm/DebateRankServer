package io.micheal.debatescout.modules;

import java.io.File;
import java.util.ArrayList;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

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
		managers = new ArrayList<WorkerPool>();
	}
	
	public void add(WorkerPool manager) {
		managers.add(manager);
	}
	
	public void start() throws PoolSizeException {
		int count = POOL_LENGTH / managers.size();
		if(count == 0)
			throw new PoolSizeException(count, POOL_LENGTH);
		for(WorkerPool manager : managers)
			manager.start(count);
	}
	
}
