package com.github.anaelcarvalho.simpleurlshortener.services;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HitcountUpdateExecutorService {
	private ExecutorService executor = null;
	private static HitcountUpdateExecutorService hitcountUpdateExecutorService = null;
	private static int threads = 2;
	
	public static void setThreadNumber(int threads) {
		HitcountUpdateExecutorService.threads = threads;
	}

	public static HitcountUpdateExecutorService getInstance() throws SQLException {
		if (hitcountUpdateExecutorService == null) {
			hitcountUpdateExecutorService = new HitcountUpdateExecutorService();
			hitcountUpdateExecutorService.initialize();
		}
		return hitcountUpdateExecutorService;
	}

	public void initialize() {
		executor = Executors.newFixedThreadPool(threads);
	}

	public void submitTask(Runnable command) {
		executor.execute(command);
	}
	
	@Override
	protected void finalize() throws Throwable {
		if(executor != null) {
			executor.shutdown();
			while (!executor.isTerminated()); //finish tasks
		}
		super.finalize();
	}
	
	
}
