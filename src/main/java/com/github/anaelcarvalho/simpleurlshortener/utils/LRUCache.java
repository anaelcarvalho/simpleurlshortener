package com.github.anaelcarvalho.simpleurlshortener.utils;

import java.util.LinkedHashMap;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5496805484562832030L;

	private int size;
    
    public LRUCache(int size) { 
        super(size+1, 1, true); //do not rehash; use access order
        this.size = size;
    }

	@Override
	protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
		return (size() > this.size); //remove only if capacity is exceeded
	}
    
}
