package com.github.anaelcarvalho.simpleurlshortener.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.dbcp2.BasicDataSource;

import com.github.anaelcarvalho.simpleurlshortener.model.Shortener;
import com.github.anaelcarvalho.simpleurlshortener.utils.LRUCache;

public class ShortenerDAO {
	private static String SQL_CREATE_TABLE = "CREATE TABLE SHORTENED_URL "
			+ "(URL_ID          BIGINT NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), "
			+ "URL_SHORTENER   VARCHAR (255) NOT NULL, "
			+ "URL_ORIGINAL    VARCHAR (255) UNIQUE NOT NULL, "
			+ "URL_INSERTED    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
			+ "URL_HITS		BIGINT NOT NULL DEFAULT 0, "
			+ "URL_AUTHTOKEN VARCHAR (255) NOT NULL)";
	private static String SQL_INSERT_SHORTENER = "INSERT INTO SHORTENED_URL(URL_SHORTENER, URL_ORIGINAL, URL_AUTHTOKEN) VALUES (?, ?, ?)";
	private static String SQL_DELETE_SHORTENER = "DELETE FROM SHORTENED_URL WHERE URL_SHORTENER = ?";
	private static String SQL_GET_SHORTENER_BY_SHORTENER = "SELECT URL_ID, URL_SHORTENER, URL_ORIGINAL, URL_INSERTED, "
			+ "URL_HITS, URL_AUTHTOKEN FROM SHORTENED_URL WHERE URL_SHORTENER = ?";
	private static String SQL_GET_SHORTENER_BY_URL = "SELECT URL_ID, URL_SHORTENER, URL_ORIGINAL, URL_INSERTED, "
			+ "URL_HITS, URL_AUTHTOKEN FROM SHORTENED_URL WHERE URL_ORIGINAL = ?";
	private static String SQL_INCREMENT_HIT_COUNT = "UPDATE SHORTENED_URL SET URL_HITS = (URL_HITS+1) WHERE URL_SHORTENER = ?";
	private static String SQL_GET_ALL_SHORTENERS = "SELECT URL_ID, URL_SHORTENER, URL_ORIGINAL, URL_INSERTED, "
			+ "URL_HITS, URL_AUTHTOKEN FROM SHORTENED_URL OFFSET ? ROWS";
	private static String SQL_GET_SHORTENER_COUNT = "SELECT COUNT(*) AS COUNT FROM SHORTENED_URL";
	
	private BasicDataSource ds;
	private static ShortenerDAO shortenerDAO = null;
	private static String dbName = "dbase";
	private static Map<String, Shortener> lruCache = null;
	private static Integer cacheSize = 4096;
	private static final Logger logger = Logger.getLogger(ShortenerDAO.class.getName());
	
	private static String DERBY_EMBEDDEDDRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
	private static String DERBY_TABLE_ALREADY_EXISTS_ERROR_CODE = "X0Y32";
	
	public static void setDbName(String dbName) {
		ShortenerDAO.dbName = dbName;
	}
	
	public static void setCacheSize(Integer cacheSize) {
		ShortenerDAO.cacheSize = cacheSize;
	}
	
	public static ShortenerDAO getInstance() throws SQLException {
		if (shortenerDAO == null) {
			shortenerDAO = new ShortenerDAO();
			lruCache = Collections.synchronizedMap(new LRUCache<String, Shortener>(cacheSize));
			shortenerDAO.ds = new BasicDataSource();
			shortenerDAO.ds.setDriverClassName(DERBY_EMBEDDEDDRIVER);
			shortenerDAO.ds.setUrl("jdbc:derby:"+dbName+";create=true");
			shortenerDAO.initSchema();
		}
		return shortenerDAO;
	}

	private void initSchema() throws SQLException {
		try(Connection conn = shortenerDAO.ds.getConnection();
				PreparedStatement preparedStatement = 
					conn.prepareStatement(SQL_CREATE_TABLE, Statement.NO_GENERATED_KEYS)) {
			preparedStatement.execute();
		} catch (SQLException e) {
			if(!e.getSQLState().equals(DERBY_TABLE_ALREADY_EXISTS_ERROR_CODE)) {
				logger.log(Level.SEVERE, "ERROR CREATING DB SCHEMA", e);
				throw e;
			} 
		}
	}
	
	public Boolean insertShortener(String shortener, String url, String authToken) {
		try(Connection conn = shortenerDAO.ds.getConnection();
				PreparedStatement preparedStatement = 
					conn.prepareStatement(SQL_INSERT_SHORTENER)) {
			preparedStatement.setString(1, shortener);
			preparedStatement.setString(2, url);
			preparedStatement.setString(3, authToken);
			preparedStatement.execute();
			return true;
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO INSERT URL SHORTENER " + shortener + " FOR URL " + url, e);
			return false;
		}
	}

	public Boolean deleteShortener(String shortener) {
		try(Connection conn = shortenerDAO.ds.getConnection();
				PreparedStatement preparedStatement = 
					conn.prepareStatement(SQL_DELETE_SHORTENER)) {
			preparedStatement.setString(1, shortener);
			preparedStatement.execute();
			if(lruCache.containsKey(shortener)) {
				lruCache.remove(shortener);
			}
			return true;
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO DELETE URL SHORTENER " + shortener, e);
			return false;
		}
	}
	
	public Shortener getShortenerByShortener(String shortener, Boolean useCache) {
		Shortener result = null;
		if(useCache && lruCache.containsKey(shortener)) {
			return lruCache.get(shortener);
		} else {
	        try(Connection conn = shortenerDAO.ds.getConnection();
	        		PreparedStatement preparedStatement = 
	        				createGetShortenerByShortenerStatement(conn, shortener);
	                ResultSet rs = preparedStatement.executeQuery();) {
				while(rs.next()) {
					result = new Shortener(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getLong(5), rs.getString(6));
				}
				if(useCache) {
					lruCache.put(shortener, result);
				}
			} catch (SQLException e) {
				logger.log(Level.SEVERE, "UNABLE TO GET URL SHORTENER " + shortener, e);
			}
		}
        return result;
	}
	
	public List<Shortener> getAllShorteners(Integer pageNumber, Integer pageSize) {
		List<Shortener> result = new ArrayList<Shortener>();
        try(Connection conn = shortenerDAO.ds.getConnection();
        		PreparedStatement preparedStatement = 
        				createGetAllShortenersStatement(conn, pageNumber, pageSize);
                ResultSet rs = preparedStatement.executeQuery();) {
			while(rs.next()) {
				result.add(new Shortener(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getLong(5), rs.getString(6)));
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO GET URL SHORTENER LIST", e);
		}
        return result;
	}
	
	public Shortener getShortenerByUrl(String url) {
		Shortener result = null;
        try(Connection conn = shortenerDAO.ds.getConnection();
        		PreparedStatement preparedStatement = 
        				createGetShortenerByUrlStatement(conn, url);
                ResultSet rs = preparedStatement.executeQuery();) {
			while(rs.next()) {
				result = new Shortener(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4), rs.getLong(5), rs.getString(6));
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO GET URL " + url, e);
		}
        return result;
	}
	
	public Boolean shortenerExists(String shortener) {
        try(Connection conn = shortenerDAO.ds.getConnection();
        		PreparedStatement preparedStatement = 
        				createGetShortenerByShortenerStatement(conn, shortener);
                ResultSet rs = preparedStatement.executeQuery();) {
			if(rs.next()) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO GET URL SHORTENER " + shortener, e);
			return false;
		}
	}
	
	public Boolean urlExists(String url) {
        try(Connection conn = shortenerDAO.ds.getConnection();
        		PreparedStatement preparedStatement = 
        				createGetShortenerByUrlStatement(conn, url);
                ResultSet rs = preparedStatement.executeQuery();) {
			if(rs.next()) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO GET URL " + url, e);
			return false;
		}
	}
	
	public Boolean incrementCounter(String shortener) {
		try(Connection conn = shortenerDAO.ds.getConnection();
				PreparedStatement preparedStatement = 
						conn.prepareStatement(SQL_INCREMENT_HIT_COUNT)) {
			preparedStatement.setString(1, shortener);
			int rowCount = preparedStatement.executeUpdate();
			if(rowCount > 0) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO INCREMENT HIT COUNTER FOR URL SHORTENER " + shortener, e);
			return false;
		}
	}
	
	public Long getShortenerCount() {
		Long count = 0L;
        try(Connection conn = shortenerDAO.ds.getConnection();
        		PreparedStatement preparedStatement = 
        				conn.prepareStatement(SQL_GET_SHORTENER_COUNT);
                ResultSet rs = preparedStatement.executeQuery();) {
			if(rs.next()) {
				count = rs.getLong(1);
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "UNABLE TO GET SHORTENER COUNT", e);
		}
        return count;
	}
	
	private PreparedStatement createGetShortenerByShortenerStatement(Connection conn, String shortener) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_GET_SHORTENER_BY_SHORTENER);
		preparedStatement.setString(1, shortener);
		return preparedStatement;
	}
	
	private PreparedStatement createGetShortenerByUrlStatement(Connection conn, String url) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_GET_SHORTENER_BY_URL);
		preparedStatement.setString(1, url);
		return preparedStatement;
	}
	
	private PreparedStatement createGetAllShortenersStatement(Connection conn, Integer pageNumber, Integer pageSize) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_GET_ALL_SHORTENERS);
		preparedStatement.setInt(1, pageNumber*pageSize);
		preparedStatement.setMaxRows(pageSize);
		return preparedStatement;
	}
}
