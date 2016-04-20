package com.github.anaelcarvalho.simpleurlshortener;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.ext.ContextResolver;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.moxy.json.MoxyJsonConfig;
import org.glassfish.jersey.moxy.json.MoxyJsonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.mustache.MustacheMvcFeature;

import com.github.anaelcarvalho.simpleurlshortener.dao.ShortenerDAO;
import com.github.anaelcarvalho.simpleurlshortener.services.HitcountUpdateExecutorService;
import com.github.anaelcarvalho.simpleurlshortener.services.ShortenerService;

public class RestServer 
{
    public static void main( String[] args ) throws Exception
    {
    	int port = 9999;
    	
    	Options options = new Options();
    	options.addOption("p", "port", true, "jetty listening port (default 9999)");
    	options.addOption("d", "database", true, "derby database to use (default dbase on execution path)");
    	options.addOption("c", "cachesize", true, "maximum number of cached entries in LRU cache (default 4096)");
    	options.addOption("t", "threads", true, "number of threads for background task processor (default 2)");
    	options.addOption("h", "help", false, null);
    	
    	CommandLineParser parser = new DefaultParser();
    	CommandLine cmd = null;
    	
    	try {
    		cmd = parser.parse(options, args);
    	} catch(ParseException e) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp("simpleurlshortener", "ERROR: Invalid args, check usage...", options, null, false);
	    	return;
    	}
    	
    	if(cmd.hasOption("h")) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp("simpleurlshortener", options );
	    	return;
    	}

    	try {
	    	if(cmd.hasOption("p")) {
	    		port = Integer.parseInt(cmd.getOptionValue("p"));
	    	}
    	} catch(NumberFormatException e) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp("simpleurlshortener", "ERROR: Invalid format for port, check usage...", options, null, false);
	    	return;
    	}
    	if(cmd.hasOption("d")) {
    		ShortenerDAO.setDbName(cmd.getOptionValue("d"));
    	}
    	try {
	    	if(cmd.hasOption("c")) {
	    		ShortenerDAO.setCacheSize(Integer.parseInt(cmd.getOptionValue("c")));
	    	}
    	} catch(NumberFormatException e) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp("simpleurlshortener", "ERROR: Invalid format for cachesize, check usage...", options, null, false);
	    	return;
    	}
    	try {
	    	if(cmd.hasOption("t")) {
	    		HitcountUpdateExecutorService.setThreadNumber(Integer.parseInt(cmd.getOptionValue("t")));
	    	}
    	} catch(NumberFormatException e) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp("simpleurlshortener", "ERROR: Invalid format for thread number, check usage...", options, null, false);
	    	return;
    	}
    	
    	final Map<String, String> namespacePrefixMapper = new HashMap<String, String>();
    	namespacePrefixMapper.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
    	final MoxyJsonConfig moxyJsonConfig = new MoxyJsonConfig()
    	            .setNamespacePrefixMapper(namespacePrefixMapper)
    	            .setNamespaceSeparator(':');
    	final ContextResolver<MoxyJsonConfig> jsonConfigResolver = moxyJsonConfig.resolver();

    	try {
            	ShortenerDAO.getInstance();
    	} catch (Exception e) {
    	    	System.out.println("WARNING: Error on DAO initialization");
    	}
    	try {
    	    	HitcountUpdateExecutorService.getInstance();
    	} catch (Exception e) {
    	    	System.out.println("WARNING: Error on background thread pool initialization");
    	}

        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
        
        String  baseStr  = "/static";
        URL     baseUrl  = ShortenerService.class.getResource(baseStr); 
        String  basePath = baseUrl.toExternalForm();
        
        ServletHolder holderHome = new ServletHolder("static", DefaultServlet.class);
        holderHome.setInitParameter("resourceBase",basePath);
        holderHome.setInitParameter("dirAllowed","true");
        holderHome.setInitParameter("pathInfoOnly","true");
        holderHome.setInitOrder(0);
        context.addServlet(holderHome,"/static/*");
        
    	ResourceConfig config = new ResourceConfig()
    			.packages("com.github.anaelcarvalho.simpleurlshortener.services")
    			.property(MustacheMvcFeature.TEMPLATE_BASE_PATH, "/templates")
    			.register(org.glassfish.jersey.server.mvc.mustache.MustacheMvcFeature.class)
    			.register(com.github.anaelcarvalho.simpleurlshortener.services.ShortenerService.class)
    			.register(MoxyJsonFeature.class)
    			.register(jsonConfigResolver);
        ServletHolder jerseyServlet = new ServletHolder("jersey", new org.glassfish.jersey.servlet.ServletContainer(config));
        jerseyServlet.setInitOrder(1);
        jerseyServlet.setInitParameter("jersey.config.server.provider.packages","com.github.anaelcarvalho.simpleurlshortener.services");
        context.addServlet(jerseyServlet, "/*");

        server.start();
        server.join();
    }
}
