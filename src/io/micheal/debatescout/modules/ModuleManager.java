package io.micheal.debatescout.modules;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

public class ModuleManager {
	public static final int POOL_LENGTH;
	
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
	
	private ExecutorService executor;
	
	public ModuleManager() {
        executor = Executors.newFixedThreadPool(POOL_LENGTH);
	}
	
	public int getActiveCount() {
		return ((ThreadPoolExecutor)executor).getActiveCount();
	}
	
	public void newModule(Runnable runnable) {
		executor.execute(runnable);
	}
	
	public void shutdown() {
		executor.shutdown();
	}
}
