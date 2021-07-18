package net.debaterank.server.modules;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class WorkerPool {

	private ExecutorService executor;
	private ArrayList<Runnable> queue;
	
	public WorkerPool() {
		queue = new ArrayList<>();
	}
	
	public int getActiveCount() {
	    if(executor == null)
	        return 0;
		return ((ThreadPoolExecutor)executor).getActiveCount();
	}
	
	public void newModule(Runnable runnable) {
		if(executor == null)
			queue.add(runnable);
		else {
			executor.execute(runnable);
		}
	}
	
	public void start(int pool_length) {
		executor = Executors.newFixedThreadPool(pool_length);
		for (Runnable r : queue)
			newModule(r);
		queue.clear();
	}

	public void shutdown() {
		executor.shutdown();
	}
}
