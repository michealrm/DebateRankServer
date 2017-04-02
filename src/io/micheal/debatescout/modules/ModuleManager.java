package io.micheal.debatescout.modules;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import io.micheal.debatescout.Module;

public class ModuleManager {
	public static final int POOL_LENGTH = 100;
	
	private ExecutorService executor;
	
	public ModuleManager() {
        executor = Executors.newFixedThreadPool(POOL_LENGTH);
	}
	
	public int getActiveCount() {
		return ((ThreadPoolExecutor)executor).getActiveCount();
	}
	
	public void newModule(Module module) {
		executor.execute(module);
	}
	
	public void shutdown() {
		executor.shutdown();
	}
}
