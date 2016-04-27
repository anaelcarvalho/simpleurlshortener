Simple URL Shortener
========================================

This is a simple tool implementing an URL shortener application using an embedded Jetty server and a Derby database, implementing a RESTful API. An admin frontend is also provided.

## Requirements

|JDK 1.8+|
|---|

## Building from source

```bash
mvn clean install assembly:single
```

## Running

```bash
java -jar target/simpleurlshortener.jar
```

Multiple arguments are accepted:

```bash
java -jar target/simpleurlshortener.jar -h

usage: simpleurlshortener
 -c,--cachesize <arg>   maximum number of cached entries in LRU cache
                        (default 4096)
 -d,--database <arg>    derby database to use (default dbase on execution
                        path)
 -h,--help
 -p,--port <arg>        jetty listening port (default 9999)
 -t,--threads <arg>     number of threads for background task processor
                        (default 2)
```
## Admin frontend (beta)

Point browser to local machine address and port (default localhost and port 9999):

```
http://localhost:9999/
```

## API documentation

Point browser to path /static/swagger :

```
http://localhost:9999/static/swagger/
```
