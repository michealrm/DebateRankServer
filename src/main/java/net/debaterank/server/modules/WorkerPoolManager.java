package net.debaterank.server.modules;

import net.debaterank.server.util.ConfigUtil;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;

public class WorkerPoolManager {

	public static final int POOL_LENGTH = ConfigUtil.getWorkerPoolCount();
	private Logger log;
	
	private ArrayList<WorkerPool> managers;
	
	public WorkerPoolManager() {
		managers = new ArrayList<>();
		log = LogManager.getLogger(WorkerPoolManager.class);
	}
	
	public void add(WorkerPool manager) {
		managers.add(manager);
	}

	public WorkerPool newPool() {
		WorkerPool pool = new WorkerPool();
		managers.add(pool);
		return pool;
	}

	public void clear() {
		shutdown();
		managers.clear();
	}

	public void start() throws PoolSizeException {
		if(managers.size() == 0)
			throw new PoolSizeException(managers.size(), POOL_LENGTH);
		int count = POOL_LENGTH / managers.size();
		int remainder = POOL_LENGTH % managers.size();
		if(count == 0) {
			log.warn("POOL_LENGTH / managers.size() < 0. Defaulting worker pool count to 1");
			count = 1;
		}
		for(WorkerPool manager : managers)
			if(remainder > 0) {
				manager.start(count + 1);
				remainder--;
			} else {
				manager.start(count);
			}
	}
	
	public int getActiveCount() {
		int active = 0;
		for(WorkerPool pool : managers)
			active += pool.getActiveCount();
		return active;
	}

	public void shutdown() {
		for(WorkerPool pool : managers)
			pool.shutdown();
	}
	
}
