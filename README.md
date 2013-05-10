rww-play 
========

This is an implementation in Play of a number of tools to build a Read-Write-Web server using Play2.x and akka.
It is very early stages at present and it implements sketches of the following

* A [CORS](http://www.w3.org/TR/cors/) proxy
* An initial implementation of [Linked Data Basic Profile](http://www.w3.org/2013/ldp/wiki/Main_Page)

This currently works in the [TLS branch of the bblfish fork of Play 2.x](https://github.com/bblfish/Play20), which comes with TLS support and a few more patches.

We use [Travis CI](http://travis-ci.org/) to verify the build: [![Build Status](https://travis-ci.org/read-write-web/rww-play.png)](http://travis-ci.org/read-write-web/rww-play)



Getting going
-------------

* You need Java 7 at least - the official Oracle JVM or another one based on [the GPLed code](http://openjdk.java.net/): removing the dependency on Oracle's JVM will require [publishing of the GPLed java security libs](http://stackoverflow.com/questions/12982595/openjdk-sun-security-libs-on-maven)
* clone [this project](https://github.com/read-write-web/rww-play) 

```bash
 $ git clone git://github.com/read-write-web/rww-play.git 
```

* You can then get the version of play that corresponds with the release of play above by either
 * downloading a pre-compiled version from [bblfish's Play20-TLS repository](http://bblfish.net/work/repo/builds/Play2/). The version of play should match the version linked to from the submodule `Play2.0` inside the repository.
 * compile the [bblfish's TLS branch of Play 2.0](https://github.com/bblfish/Play20) that is available as a [submodule](http://git-scm.com/book/en/Git-Tools-Submodules) in the cloned repository as follows:

```
 $ git submodule init
 $ git submodule update
 $ cd Play20/framework
 $ ./build
 > publish-local 
  ... [exit scala shell]
 $ cd ../..
```

* From the home directory of this project, start the previously compiled Play2.0 server you can run play on `http` port 9000 

```bash
$ Play20/play
> run
```

* to start Play in secure mode with lightweight client certificate verification (for WebID)

```bash
 $ Play20/play
 > run  -Dhttps.port=8443 -Dhttps.trustStore=noCA
```

* You can also start the server so that it only accepts WebID certificates - which we will currently
 assume are those signed by an agent named "CN=WebID,O=∅". This is experimental! The previous solution is recommended.

```bash
 $ Play20/play
 > run  -Dhttps.port=8443 -Dhttps.trustStore=webid.WebIDTrustManager
```


Usage 
-----

## WebID test

1. get yourself a WebID certificate ( e.g. [My-Profile](https://my-profile.eu/profile.php) will give you a nice one ), or use
  the certgen service described above.
2. Use the browser you got a certificate above to connect to [https://localhost:8443/test/webid/hello+world](https://localhost:8443/test/webid/eg). Your browser will request a certificate from you and return a (way to simple message) - more advanced versions of this server will show a lot more info... 

The code to run this is a few lines in [Application](https://github.com/read-write-web/rww-play/blob/master/app/controllers/Application.scala#L17):

```scala
  import JenaConfig._
  implicit val JenaWebIDVerifier = new WebIDVerifier[Jena]()


  val JenaWebIDAuthN = new WebIDAuthN[Jena]()

  implicit val idGuard: IdGuard[Jena] = WebAccessControl[Jena](linkedDataCache)
  def webReq(req: RequestHeader) : WebRequest[Jena] =
    new PlayWebRequest[Jena](new WebIDAuthN[Jena],new URL("https://localhost:8443/"),meta _)(req)

  // Authorizes anyone with a valid WebID
  object WebIDAuth extends Auth[Jena](idGuard,webReq _)

 def webId(path: String) = WebIDAuth() { authFailure =>
    Unauthorized("You are not authorized "+ authFailure)
  }
  { authReq =>
      Ok("You are authorized for " + path + ". Your ids are: " + authReq.user)
  }
  ```

The [Auth](https://github.com/read-write-web/rww-play/blob/master/app/org/w3/readwriteweb/play/auth/AuthZ.scala#L33) class can be tuned for any type of authentication, by passing the relevant `authentication` and `acl` function to it.  The WebId Authentication code [WebIDAuthN](https://github.com/read-write-web/rww-play/blob/master/app/org/w3/play/auth/WebIDAuthN.scala) is quite short and makes use of the `Claim`s monad to help isolate what is verified and what is not.

## Linked Data Platform

A very initial implementation of the (LDP)[http://www.w3.org/2013/ldp/hg/ldp.html] spec is implemented here. At present it does not save the data! But you can try it out by using the Linked Data Collection LDC available at http://localhost:900/2013/

First you can create a new resource with POST
```bash
$ curl -X POST -i  -H "Content-Type: text/turtle; utf-8"  -H "Slug: card" http://localhost:9000/2013/ -d @eg/card.ttl
...
HTTP/1.1 200 OK
Location: /2013/card
```

This will then create a remote resource at the given location, in the above example
`http://localhost:9000/2013/card`

```bash
$ curl  -i  -H "Accept: text/turtle"  http://localhost:9000/2013/card
HTTP/1.1 200 OK
Link: <http://localhost:9000/2013/card;acl>; rel=acl
Content-Type: text/turtle
Content-Length: 236

<#i> <http://xmlns.com/foaf/0.1/name> "Your Name"^^<http://www.w3.org/2001/XMLSchema#string> ;
    <http://xmlns.com/foaf/0.1/knows> <http://bblfish.net/people/henry/card#me> .

<> <http://www.w3.org/2000/01/rdf-schema#member> <card> .
```

Then you can POST some more triples on that resource to APPEND to it,
and you can GET it and DELETE it. 

For example to append the triples in some file 'other.ttl' you can use. ( Note this has
not been adopted by the LDP WG, though there is an issue open for it )

```bash
$ curl -i -X POST -H "Content-Type: text/turtle" http://localhost:9000/2013/card -d @eg/more.ttl
```

if you `GET` the `card` with curl as shown above, the server should now show your
content with a few more relations. You can even fetch it in a different representation
such as the older rdf/xml

```bash
$ curl -i -X GET -H "Accept: application/rdf+xml" http://localhost:9000/2013/card
```

finally if you wish to delete it you can run

```bash
$ curl -i -X DELETE http://localhost:9000/2013/card
```

A GET on that resource will from then on return an error.

To make a collection you can use the MKCOL method as defined by [RFC4918: HTTP Extensions for WebDAV](http://tools.ietf.org/html/rfc4918#section-9.3)

```bash
$ curl -i -X MKCOL -H "Expect:" http://localhost:9000/2013/pix/
HTTP/1.1 201 Created
```

But the LDP way to do this is to POST a new container.

```bash
$ curl -i -X POST -H "Content-Type: text/turtle" -H "Slug: type" -H "Expect:" http://localhost:9000/2013/ -d @eg/newContainer.ttl 
HTTP/1.1 201 Created
Location: http://localhost:9000/2013/type
Content-Length: 0
```

You can then GET the content of the container

```bash
HTTP/1.1 200 OK
Link: <http://localhost:9000/2013/type;acl>; rel=acl
Content-Type: text/turtle
Content-Length: 378


<http://localhost:9000//2013/> a <http://www.w3.org/ns/ldp#Container> ;
    <http://xmlns.com/foaf/0.1/topic> "A container for some type X of resources"^^<http://www.w3.org/2001/XMLSchema#string> ;
    <http://xmlns.com/foaf/0.1/maker> <http://localhost:9000/card#me> .

<http://localhost:9000/2013/> <http://www.w3.org/2000/01/rdf-schema#member> <http://localhost:9000/2012/type> .
```

## Web Access Control with Linked Data
create card
```bash
$ curl -X POST -k -i -H "Content-Type: text/turtle; utf-8" -H "Slug: card.acl"  --cert eg/test-localhost.pem:test  -d @eg/card-acl.ttl https://localhost:8443/2013/
```
le GET ne marche pas
```bash
$ curl -k -i --cert eg/test-localhost.pem:test -X GET -H "Accept: text/turtle" https://localhost:8443/2013/card
```
append the triples using the deprecated POST onto the acl
```bash
$ curl -X POST -k -i -H "Content-Type: text/turtle; utf-8" -H "Slug: card.acl"  --cert eg/test-localhost.pem:test  -d @eg/card-acl.ttl https://localhost:8443/2013/
```
it works:
```bash
$ curl -k -i --cert eg/test-localhost.pem:test -X GET -H "Accept: text/turtle" https://localhost:8443/2013/card
```
Next we can publish a couch surfing opportunity
```bash
$ curl -X POST -k -i -H "Content-Type: text/turtle; utf-8"  --cert eg/test-localhost.pem:test  -H "Slug: couch" -d @eg/couch.ttl https://localhost:8443/2013/
```
Initially this is unreachable by any one ( should the acls perhaps always point with a wac:include to the directory acl by default? )
```bash
$ curl -k -i -X GET -H "Accept: application/rdf+xml" --cert eg/test-localhost.pem:test https://localhost:8443/2013/couch
```
So we add the couch acl
```bash
$ curl -v -X POST -k -i -H "Content-Type: text/turtle; utf-8"  -H "Slug: card" --cert eg/test-localhost.pem:test  -d @eg/couch-acl.ttl https://localhost:8443/2013/couch.acl
```
This makes it available to the test user and the members of the WebID and OuiShare groups.
```bash
$ curl -k -i -X GET -H "Accept: application/rdf+xml" --cert eg/test-localhost.pem:test https://localhost:8443/2013/couch
```
or in Turtle
```bash
$  curl -k -i -X GET -H "Accept: text/turtle" --cert eg/test-localhost.pem:test https://localhost:8443/2013/couch
```

### Creating a WebID Certificate

After starting your server you can point your browser to [http://localhost:9000/srv/certgen](http://localhost:9000/srv/certgen?webid=http%3A%2F%2Flocalhost%3A8443%2F2013%2Fcert%23me) or to [the service over https ](https://localhost:8443/srv/certgen?webid=http%3A%2F%2Flocalhost%3A8443%2F2013%2Fcert%23me) and create yourself a certificate. For testing purposes and in order to be able to work without the need for network connectivity use `http://localhost:8443/2013/cert#me'. The WebID Certificate will be signed by the agent with Distinguished Name "CN=WebID,O=∅" and added by your browser to its keychain.

( Todo: later we will add functionality to add create a local webid that also published the RDF )
To make the WebID valid you will need to publish the relavant rdf at that document location as explained in [the WebID spec](http://www.w3.org/2005/Incubator/webid/spec/#publishing-the-webid-profile-document)

### Secure LDP examples


For Web Access Control with [WebID](http://webid.info/) you have to start play in secure mode ( see above ) and  create a WebID.
## CORS 

(no longer working right now)

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
Date: Tue, 10 Jul 2013 08:56:24 GMT
ETag: "125d8606-2ee6-45fd305ed0440"

@prefix dc:      <http://purl.org/dc/elements/1.1/> .
@prefix geo:     <http://www.w3.org/2003/01/geo/wgs84_pos#> .
```

The usual use case for fetching such a resource is to make the query in JavaScript, using a library
such as [rdflib](https://github.com/linkeddata/rdflib.js)

### Todo

Query support as shown below no longer works right now.

```
$ curl -X POST -H "Content-Type: application/sparql-query; charset=UTF-8" --data-binary "SELECT ?p WHERE { <http://bblfish.net/people/henry/card#me> <http://xmlns.com/foaf/0.1/knows> ?p . } " -i http://localhost:9000/2013/card.ttl
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
curl -X POST -H "Content-Type: application/sparql-query; charset=UTF-8" -H "Accept: application/sparql-results+json" --data-binary "SELECT ?p WHERE { <http://bblfish.net/people/henry/card#me> <http://xmlns.com/foaf/0.1/knows> [ <http://xmlns.com/foaf/0.1/name> ?p ] . } " -i http://localhost:9000/2013/card.ttl
```

## Proxy a Web Site

Want to try out what an existing Web site would look like with WebID enabled? Just proxy it.
Note: this currently only works well for sites whose URLs are all relative.

To do this you need to do three things:

1. In `conf/application.conf` set the `rww.proxy...` properties
2. If you did not change `rww.proxy.acl` property then go to ```test_www/meta.ttl``` and edit the acls there.
3. In `conf/routes` uncomment the `controllers.AuthProxyApp.proxy(rg)` . This has to be the root for urls to work correctly. 


You should then be able to run RWW_Play on the tls port

```
> run  -Dhttps.port=8443 -Dhttps.trustStore=noCA
```

and on going to the http://localhost:8443/ and see a version of the remote server.

Todo: 
 * make the access control better by not having the first page ask for a certificate.
 * write a library to easily hook into the access control system so that mappers from WebIDs to other systems can be built quickly
 * enable other methods such as PUT/POST/DELETE...
 * have the metadata be more flexible - currently it only looks in one file, the acl system should follow links

Development Tricks
------------------

# publishing libraries to local play repository

If you are working on a library that is needed as part of this project, and in order
to avoid having to upload that library to a remote server during the debugging phase 
( which slows down development ) then you need to publish those libaries in the local
Play repository that you are using. So before running the `publish-local` command
for your library, run the following

```bash
$ export $play=$rwwHome/Play20
$ export SBT_PROPS="-Dsbt.ivy.home=$play/repository -Dplay.home=$play/framework"
$ ./sbt
> publish-local
```

Licence
-------

   Copyright 2013 Henry Story

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
