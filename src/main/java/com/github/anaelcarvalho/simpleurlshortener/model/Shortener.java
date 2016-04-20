package com.github.anaelcarvalho.simpleurlshortener.model;

import java.util.Date;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement
@XmlType(propOrder = { "id", "shortener", "url", "dateInserted", "hitCount", "authToken" })
public class Shortener {
	private Long id;
	private String shortener;
	private String url;
	private Date dateInserted;
	private Long hitCount;
	private String authToken;
	
	public Shortener(Long id, String shortener, String url, Date dateInserted, Long hitCount, String authToken) {
		super();
		this.id = id;
		this.shortener = shortener;
		this.url = url;
		this.dateInserted = dateInserted;
		this.hitCount = hitCount;
		this.authToken = authToken;
	}

	public Shortener(String shortener, String url) {
		super();
		this.shortener = shortener;
		this.url = url;
	}

	public Shortener() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getShortener() {
		return shortener;
	}

	public void setShortener(String shortener) {
		this.shortener = shortener;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Date getDateInserted() {
		return dateInserted;
	}

	public void setDateInserted(Date dateInserted) {
		this.dateInserted = dateInserted;
	}

	public Long getHitCount() {
		return hitCount;
	}

	public void setHitCount(Long hitCount) {
		this.hitCount = hitCount;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}
	
}
