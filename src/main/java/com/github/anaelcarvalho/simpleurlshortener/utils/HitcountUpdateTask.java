package com.github.anaelcarvalho.simpleurlshortener.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.anaelcarvalho.simpleurlshortener.dao.ShortenerDAO;

public class HitcountUpdateTask implements Runnable {
	private final String shortener;
	private static final Logger logger = Logger.getLogger(HitcountUpdateTask.class.getName());
	
	public HitcountUpdateTask(String shortener) {
		this.shortener = shortener;
	}
	
	@Override
	public void run() {
		try {
			ShortenerDAO.getInstance().incrementCounter(shortener);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "UNABLE TO INCREMENT HIT COUNTER FOR URL SHORTENER " + shortener, e);
		}
	}

}
