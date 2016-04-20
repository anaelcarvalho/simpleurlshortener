package com.github.anaelcarvalho.simpleurlshortener.services;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.codec.digest.DigestUtils;
import org.glassfish.jersey.server.mvc.Template;

import com.github.anaelcarvalho.simpleurlshortener.dao.ShortenerDAO;
import com.github.anaelcarvalho.simpleurlshortener.model.Shortener;
import com.github.anaelcarvalho.simpleurlshortener.utils.HitcountUpdateTask;

@Path("/")
public class ShortenerService {
	ShortenerDAO shortenerDao;
	private static final Logger logger = Logger.getLogger(ShortenerService.class.getName());
	
	@GET
	@Produces(MediaType.TEXT_HTML)
    @Template(name="/index.mustache")
    public String get() {
        return "";
    }
	
	@GET
	@Path("{shortenedUrl}")
    public Response getUrl(@PathParam("shortenedUrl") String shortenedUrl) {
		try {
			Shortener shortener = ShortenerDAO.getInstance().getShortenerByShortener(shortenedUrl, true);
			if(shortener != null) {
				URI originalUrl = new URI(shortener.getUrl());
				HitcountUpdateExecutorService.getInstance().submitTask(new HitcountUpdateTask(shortenedUrl));
				return Response.status(301).location(originalUrl).build();
			} else {
				return Response.status(404).build();
			}
		} catch (SQLException | URISyntaxException e) {
			logger.log(Level.SEVERE, "ERROR ON SERVICE GET {shortenedUrl}", e);
			return Response.status(500).build();
		}
    }
	
	@GET
	@Path("{shortenedUrl}/info")
	@Produces(MediaType.APPLICATION_JSON)
    public Response getUrlStats(@PathParam("shortenedUrl") String shortenedUrl) {
		try {
			Shortener shortener = ShortenerDAO.getInstance().getShortenerByShortener(shortenedUrl, false);
			if(shortener != null) {
				return Response.status(200).entity(shortener).build();
			} else {
				return Response.status(404).build();
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "ERROR ON SERVICE GET {shortenedUrl}/info", e);
			return Response.status(500).build();
		}
    }
	
	@GET
	@Path("list/{pageNumber}")
	@Produces(MediaType.APPLICATION_JSON)
    public Response getAllShorteners(@PathParam("pageNumber") Integer pageNumber, @QueryParam("pageSize") Integer pageSize) {
		try {
			if(pageNumber == null)
				pageNumber = 0;
			if(pageSize == null) {
				pageSize = 30;
			}
			Long shortenerCount = ShortenerDAO.getInstance().getShortenerCount();
			List<Shortener> shortener = ShortenerDAO.getInstance().getAllShorteners(pageNumber, pageSize);
			if(shortenerCount > 0 && shortener != null && shortener.size() > 0) {
				GenericEntity<List<Shortener>> list = new GenericEntity<List<Shortener>>(shortener) {};
				ResponseBuilder responseBuilder = Response.status(200).entity(list);
				responseBuilder.link("/list/0", "first");
				responseBuilder.link("/list/"+(((shortenerCount + pageSize - 1)/pageSize)-1), "last");
				if(pageNumber > 0) {
					responseBuilder.link("/list/"+(pageNumber-1), "previous");
				}
				if((pageNumber+1)*pageSize < shortenerCount) {
					responseBuilder.link("/list/"+(pageNumber+1), "next");
				}
				
				return responseBuilder.build();
			} else {
				return Response.status(404).build();
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "ERROR ON SERVICE GET list/{pageNumber}", e);
			return Response.status(500).build();
		}
    }
	
	@POST
	@Path("create")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createShortenedUrl(@FormParam("url") String url) {
		if(url == null || url.isEmpty()) {
			return Response.status(400).build(); 
		}
		try {
			url = URLDecoder.decode(url, "UTF-8");
			URL originalUrl = new URL(url);
			String hash = DigestUtils.sha1Hex(originalUrl.toURI().toASCIIString());
			int hashLength = hash.length();
			String hashToFind = "";
			for(int i=5; i<hashLength; i++) {
				hashToFind = hash.substring(0, i+1);
				Shortener shortener = ShortenerDAO.getInstance().getShortenerByShortener(hashToFind, false);
				if(shortener == null) {
					break;
				} else {
					URI storedUrl = new URI(shortener.getUrl());
					if(originalUrl.toURI().compareTo(storedUrl) == 0) {
						return Response.status(409).location(new URI(shortener.getShortener())).build();
					}
				}
			}
			
			SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
			String randomNum = new Integer(prng.nextInt()).toString();
			String authToken = Base64.getEncoder().encodeToString(DigestUtils.sha1(randomNum));

			if(ShortenerDAO.getInstance().insertShortener(hashToFind, originalUrl.toURI().toASCIIString(), authToken)) {
				return Response.status(201).location((new URI(hashToFind))).header("WWW-Authenticate", authToken).build();
			} else {
				return Response.status(500).build();
			}
		} catch (URISyntaxException | MalformedURLException e) {
			return Response.status(400).build();
		} catch (SQLException | NoSuchAlgorithmException | UnsupportedEncodingException e) {
			logger.log(Level.SEVERE, "ERROR ON SERVICE POST create", e);
			return Response.status(500).build();
		}		
    }
	
	@DELETE
	@Path("{url}")
    public Response deleteShortenedUrl(@PathParam("url") String url, @HeaderParam("Authorization") String authToken) {
		try {
			if(authToken == null || authToken.isEmpty()) {
				return Response.status(401).build();
			}
			Shortener shortener = ShortenerDAO.getInstance().getShortenerByShortener(url, false);
			if(shortener != null) {
				if(shortener.getAuthToken().compareTo(authToken) == 0) {
					if(ShortenerDAO.getInstance().deleteShortener(url)) {
						return Response.status(200).build();
					} else {
						return Response.status(500).build();
					}
				} else {
					return Response.status(401).build();
				}
			} else {
				return Response.status(404).build();
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "ERROR ON SERVICE DELETE {url}", e);
			return Response.status(500).build();
		}
    }
}