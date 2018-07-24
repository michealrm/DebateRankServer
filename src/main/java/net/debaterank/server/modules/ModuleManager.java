package net.debaterank.server.modules;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ModuleManager {
	
	private ExecutorService executor;
	
	public ModuleManager() {
        executor = Executors.newCachedThreadPool();
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