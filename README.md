rww-play 
========

This is an implementation in Play of a number of tools to build a Read-Write-Web server using Play2.x and akka.
It is very early stages at present and it implements sketches of the following

* A [CORS](http://www.w3.org/TR/cors/) proxy
* An initial implementation of [Linked Data Basic Profile](http://www.w3.org/2012/ldp/wiki/Main_Page)

This currently works in the [2.0.3-with-TLS branch of the bblfish fork of Play 2.0](https://github.com/bblfish/Play20), which comes with TLS support and a few more patches.


Getting going
-------------


* clone [this project](https://github.com/read-write-web/rww-play) and compile 
  the [bblfish's 2.0.3-with-TLS branch of Play 2.0](https://github.com/bblfish/Play20) [submodule](http://git-scm.com/book/en/Git-Tools-Submodules)
  
```bash
 $ git clone git://github.com/read-write-web/rww-play.git 
 $ git submodule init
 $ git submodule update
 $ cd Play20/framework
 $ ./build
 > publish-local 
  ... [exit scala shell]
 $ cd ../..
```
* from the home directory of this project, start the previously compiled Play2.0 server in secure mode with lightweight client certificate verification (for WebID)

```bash
 $ Play20/play
 > run  -Dhttps.port=8443 -Dhttps.trustStore=noCA
```

* You can also start the server so that it only accepts WebID certificates - which we will currently
 assume are those signed by an agent named "CN=WebID,O=∅"

```bash
 $ Play20/play
 > run  -Dhttps.port=8443 -Dhttps.trustStore=webid.WebIDTrustManager
```




Usage 
-----

### Creating a WebID Certificate

After starting your server you can go to http://localhost:9000/srv/certgen or to [the https equivalent](https://localhost:8443/srv/certgen) and create yourself a certificate for a WebID profile you may already have. The WebID will be signed by the agent with Distinguished Name "CN=WebID,O=∅" so that we can try out if making requests only for those certificates does the right thing.

( Todo: later we will add functionality to add create a local webid that also published the RDF )
To make the WebID valid you will need to publish the relavant rdf at that document location as explained in [the WebID spec](http://www.w3.org/2005/Incubator/webid/spec/#publishing-the-webid-profile-document)


### WebID test

1. get yourself a WebID certificate ( e.g. [My-Profile](https://my-profile.eu/profile.php) will give you a nice one ), or use
  the certgen service described above.
2. Use the browser you got a certificate above to connect to [https://localhost:8443/test/webid/eg](https://localhost:8443/test/webid/eg). Your browser will request a certificate from you and return a (way to simple message) - more advanced versions of this server will show a lot more info... 

The code to run this is a few lines in [Application](https://github.com/read-write-web/rww-play/blob/master/app/controllers/Application.scala#L17):

```scala
 //setup: should be moved to a special init class
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = system.dispatcher
  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
  implicit val JenaGraphFetcher = new GraphFetcher[Jena](JenaAsync.graphIterateeSelector)
  implicit val JenaWebIDVerifier = new WebIDVerifier[Jena]()

  val JenaWebIDAuthN = new WebIDAuthN[Jena]()

  // Authorizes anyone with a valid WebID
  object WebIDAuth extends Auth(JenaWebIDAuthN, _ => Future.successful(WebIDGroup),_=>Unauthorized("no valid webid"))


  def webId(path: String) = WebIDAuth { authReq =>
      Ok("You are authorized for " + path + ". Your ids are: " + authReq.user)
  }
  ```

The [Auth](https://github.com/read-write-web/rww-play/blob/master/app/org/w3/readwriteweb/play/auth/AuthZ.scala#L33) class can be tuned for any type of authentication, by passing the relevant `authentication` and `acl` function to it.  The WebId Authentication code [WebIDAuthN](https://github.com/read-write-web/rww-play/blob/master/app/org/w3/play/auth/WebIDAuthN.scala) is quite short and makes use of the `Claim`s monad to help isolate what is verified and what is not.

### CORS 

To fetch a remote rdf resource in a CORS proxy friendly manner send an HTTP GET request to  
`http://localhost:9000/srv/cors?url={remote-url}` replacing `{remoate-url}` with a URL-encoded
url.

Using the command line tool `curl` the following command fetches Dean Allemang's "rdf/xml" foaf profile
and returns it as Turtle with the needed CORS headers.

```bash
$ curl -s -i -H "Accept: text/turtle" -H "Origin: http://tricks.js"  "http://localhost:9000/srv/cors?url=http://www.topquadrant.com/people/dallemang/foaf.rdf" 
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://tricks.js
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

