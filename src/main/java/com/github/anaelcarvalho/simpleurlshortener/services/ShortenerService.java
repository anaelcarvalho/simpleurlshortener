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
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.codec.digest.DigestUtils;
import org.glassfish.jersey.server.mvc.Template;

import com.github.anaelcarvalho.simpleurlshortener.dao.ShortenerDAO;
import com.github.anaelcarvalho.simpleurlshortener.model.Shortener;
import com.github.anaelcarvalho.simpleurlshortener.utils.HitcountUpdateTask;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;

@Path("/")
@Api(value = "URL Shortener API")
public class ShortenerService {
	ShortenerDAO shortenerDao;
	private static final Logger logger = Logger.getLogger(ShortenerService.class.getName());
	
	@GET
	@Produces(MediaType.TEXT_HTML)
    @Template(name="/index.mustache")
	@ApiOperation(hidden = true, value = "")
    public String get() {
        return "";
    }
	
	@GET
	@Path("{shortener}")
	@ApiOperation(value = "Get original URL by shortener token and redirect to it",
			code = 301)
	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Shortener not found"),
			@ApiResponse(code = 500, message = "System error") })
    public Response getUrl(@ApiParam(value = "Shortener token", required = true) @PathParam("shortener") String shortenedUrl) {
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
	@Path("{shortener}/info")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Get detailed shortener information",
			response = Shortener.class)
	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Shortener not found"),
			@ApiResponse(code = 500, message = "System error") })
    public Response getUrlStats(@ApiParam(value = "Shortener token", required = true) @PathParam("shortener") String shortenedUrl) {
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
	@ApiOperation(value = "Get shortener list",
			response = Shortener.class,
			responseContainer = "List",
			responseHeaders = 
				@ResponseHeader(name = "Link", description = "Contains paging information", response = Link.class))
	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Shortener not found"),
			@ApiResponse(code = 500, message = "System error") })
    public Response getAllShorteners(
    		@ApiParam(value = "Page number", required = true, defaultValue = "0") @PathParam("pageNumber") Integer pageNumber, 
    		@ApiParam(value = "Page size", required = false, defaultValue = "30") @QueryParam("pageSize") Integer pageSize) {
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
	@ApiOperation(value = "Create new shortener",
			code = 201,
			responseHeaders = {
			@ResponseHeader(name = "Location", description = "Link for new shortener", response = URI.class),
			@ResponseHeader(name = "WWW-Authenticate", description = "Authentication token for managing shortener", response = String.class)})
	@ApiResponses(value = { 
			@ApiResponse(code = 400, message = "Invalid URL parameter"),
			@ApiResponse(code = 409, message = "A shortener already exists for the given URL", 
					responseHeaders = @ResponseHeader(name = "Location", description = "Link for the shortener", response = URI.class)),
			@ApiResponse(code = 500, message = "System error") })
    public Response createShortenedUrl(@ApiParam(value = "URL for shortener creation", required = true) @FormParam("url") String url) {
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
	@Path("{shortener}")
	@ApiOperation(value = "Delete a shortener")
	@ApiResponses(value = {
			@ApiResponse(code = 401, message = "Authentication token is required"),
			@ApiResponse(code = 404, message = "Shortener not found"),
			@ApiResponse(code = 500, message = "System error") })
    public Response deleteShortenedUrl(
    		@ApiParam(value = "Shortener to delete", required = true) @PathParam("shortener") String url, 
    		@ApiParam(value = "Authentication token", required = true) @HeaderParam("Authorization") String authToken) {
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
