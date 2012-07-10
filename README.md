rww-play 
========

This is an implementation in Play of a number of tools to build a Read-Write-Web server using Play2.x and akka.
It is very early stages at present and it implements sketches of the following

* A [CORS](http://www.w3.org/TR/cors/) proxy
* An initial implementation of [Linked Data Basic Profile](http://www.w3.org/2012/ldp/wiki/Main_Page)

This currently works in Play2.0.

Proper functioning of the CORS proxy requires the patch for the [pull request 378](https://github.com/playframework/Play20/pull/378) of
Play.

Getting going
-------------

* Install the latest version of [Play 2.0](https://github.com/playframework)
* enter the home directory and run
 > play
* start the server on port 9000
 > run


Usage 
-----


### CORS 

To fetch a remote rdf resource in a CORS proxy friendly manner send an HTTP GET request to  
`http://localhost:9000/srv/cors?url={remote-url}` replacing `{remoate-url}` with a URL-encoded
url.

Using the command line tool `curl` the following command fetches Dean Allemang's "rdf/xml" foaf profile
and returns it as Turtle with the needed CORS headers.

```bash
$ curl -s -i -H "Accept: text/turtle" -H "Origin: http://love.js"  "http://localhost:9000/srv/cors?url=http://www.topquadrant.com/people/dallemang/foaf.rdf" 
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://love.js
Last-Modified: Tue, 06 Jan 2009 16:37:29 GMT
Server: Apache
Accept-Ranges: bytes
Keep-Alive: timeout=5, max=100
Connection: Keep-Alive
Content-Length: 12006
Content-Type: application/rdf+xml
Date: Tue, 10 Jul 2012 08:56:24 GMT
ETag: "125d8606-2ee6-45fd305ed0440"

@prefix dc:      <http://purl.org/dc/elements/1.1/> .
@prefix geo:     <http://www.w3.org/2003/01/geo/wgs84_pos#> .
```

The usual use case for fetching such a resource is to make the query in JavaScript, using a library
such as [rdflib](https://github.com/linkeddata/rdflib.js)

### Linked Data

You PUT an RDF resource to the  test_www directory with a command such as 

```
$ curl -X PUT -i -T card.ttl -H "Content-Type: text/turtle; utf-8"  http://localhost:9000/2012/card.ttl
```

and you can query it on the command line with curl as follows

```
$ curl -X POST -H "Content-Type: application/sparql-query; charset=UTF-8" --data-binary "SELECT ?p WHERE { <http://bblfish.net/people/henry/card#me> <http://xmlns.com/foaf/0.1/knows> ?p . } " -i http://localhost:9000/2012/card.ttl
HTTP/1.1 200 OK
Content-Type: application/sparql-results+xml
Content-Length: 8799

<?xml version="1.0"?>
<sparql xmlns="http://www.w3.org/2005/sparql-results#">
  <head>
    <variable name="p"/>
  </head>
  <results>
    <result>
      <binding name="p">
        <uri>http://richard.cyganiak.de/foaf.rdf#cygri</uri>
 ...
```

or if you would rather it return json 

```
curl -X POST -H "Content-Type: application/sparql-query; charset=UTF-8" -H "Accept: application/sparql-results+json" --data-binary "SELECT ?p WHERE { <http://bblfish.net/people/henry/card#me> <http://xmlns.com/foaf/0.1/knows> [ <http://xmlns.com/foaf/0.1/name> ?p ] . } " -i http://localhost:9000/2012/card.ttl
```

