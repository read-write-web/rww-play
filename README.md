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

The test_www directory starts with a few files to get you going

```bash
$ cd test_www/
$ ls -al
total 48
drwxr-xr-x   4 hjs  admin   340 30 Jun 14:28 .
drwxr-xr-x  15 hjs  admin  1292  1 Jul 07:45 ..
-rw-r--r--   1 hjs  staff   218 30 Jun 14:22 .acl.ttl
-rw-r--r--   1 hjs  admin   118 30 Jun 14:27 .ttl
lrwxr-xr-x   1 hjs  admin     8 27 Jun 20:29 card -> card.ttl
-rw-r--r--   1 hjs  admin   246 30 Jun 14:26 card.acl.ttl
-rw-r--r--   1 hjs  admin   896 27 Jun 21:41 card.ttl
-rw-r--r--   1 hjs  admin   102 27 Jun 22:32 index.ttl
drwxr-xr-x   2 hjs  admin   102 27 Jun 22:56 raw
drwxr-xr-x   3 hjs  admin   204 28 Jun 12:51 test
```

The symbolic links such as `card` distinguish the default resources that can be found by an http `GET`.
They point to their default representation, in this case `card.ttl`, an rdf resource. Each resource
also comes with a Access Control List, in this example `card.acl.ttl`. Directories also have their access
control list which is published in a file named `.acl.ttl`.  These two conventions are provisional implementation
decisions, and improvements are to be expected here .

The acl for `card` just includes the acl for the directory/collection .

```bash
$ cat card.acl.ttl 
@prefix wac: <http://www.w3.org/ns/auth/acl#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

<> wac:include <.acl> .
```

The acl for the directoy allows access to all resources in the subdirectories of
`test_www` when accessed from the web as `https://localhost:8443/2013/` only to
the user `<https://localhost:8443/2013/card#me>`

```bash
$ cat .acl.ttl 
@prefix acl: <http://www.w3.org/ns/auth/acl#> . 
@prefix foaf: <http://xmlns.com/foaf/0.1/> . 

[] acl:accessToClass [ acl:regex "https://localhost:8443/2013/.*" ];  
   acl:mode acl:Read, acl:Write; 
   acl:agent <card#me> .  
```

Since card's acl includes the above directory acl only `<card#me> can read that file.

```bash
curl -i -k  -H "Accept: text/turtle"  https://localhost:8443/2013/card
curl: (56) SSL read: error:14094412:SSL routines:SSL3_READ_BYTES:sslv3 alert bad certificate, errno 0
```

As curl does not allow `WANT` TLS connections, but only NEED, a failure to authenticate closes 
the connection. Most browsers have the more friendly behavior allowing the server to return
an HTTP error code and an explanatory body.

Requesting the same resource with a `curl` that knows which client certificate to use, the request
goes through.

```bash
$ curl -i -k --cert ../eg/test-localhost.pem:test  -H "Accept: text/turtle"  https://localhost:8443/2013/card
HTTP/1.1 200 OK
Link: <https://localhost:8443/2013/card.acl>; rel=acl
Content-Type: text/turtle
Content-Length: 1037


<https://localhost:8443/2013/card#me> <http://xmlns.com/foaf/0.1/name> "Your Name"^^<http://www.w3.org/2001/XMLSchema#string> ;
    <http://xmlns.com/foaf/0.1/knows> <http://bblfish.net/people/henry/card#me> ;
    <http://www.w3.org/ns/auth/cert#key> _:node17ucdq7scx1 .

_:node17ucdq7scx1 a <http://www.w3.org/ns/auth/cert#RSAPublicKey> ;
    <http://www.w3.org/ns/auth/cert#exponent> "65537"^^<http://www.w3.org/2001/XMLSchema#integer> ;
    <http://www.w3.org/ns/auth/cert#modulus> "C13AB88098CF47FCE6B3463FC7E8762036154FE616B956544D50EE63133CC8748D692A00DAFF5331D2564BB1DD5AEF94636ED09EFFA9E776CA6B4A92022BB060BF18FC709936EF43D345289A7FD91C81801A921376D7BCC1C63BD3335FB385A01EC0B71877FCBD1E4525393CCD5F2922D68840945943A675CCAE245222E3EB99B87B180807002063CB78174C1605EA1ECFECF57264F7F60CD8C270175A1D8DD58DFC7D3C56DB273B0494B034EC185B09977CBB530E7E407206107A73CD4B49E17610559F2A81EA8E3F613C3D3C161C06FE5CB114A8522D20DED77CAAA8C761090022F9CD4AF2C8F21DF7CF05287E379225AEA6A3A6610D02C4A44AA7CEED2CC3"^^<http://www.w3.org/2001/XMLSchema#hexBinary> .
```

Notice the `Link` header above. Every resource points to its ACL file in such a header.

The client certificate in `../eg/test-localhost.pem` contains exactly the private key given in the above 
cert as you can see by comparing the modulus and exponents in both representations. This
is what allows the authentication to go through, using the (WebID over TLS protocol)[http://webid.info/spec/].

```bash
$ openssl x509 -in ../eg/test-localhost.pem -inform pem -text
Certificate:
    Data:
        Version: 3 (0x2)
        Serial Number: 13633800264985240815 (0xbd34fd1b251264ef)
    Signature Algorithm: sha1WithRSAEncryption
        Issuer: O=\xE2\x88\x85, CN=WebID
        Validity
            Not Before: May  3 19:36:33 2013 GMT
            Not After : May  3 19:46:33 2014 GMT
        Subject: O=ReadWriteWeb, CN=test@localhost
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (2048 bit)
                Modulus:
                    00:c1:3a:b8:80:98:cf:47:fc:e6:b3:46:3f:c7:e8:
                    76:20:36:15:4f:e6:16:b9:56:54:4d:50:ee:63:13:
                    3c:c8:74:8d:69:2a:00:da:ff:53:31:d2:56:4b:b1:
                    dd:5a:ef:94:63:6e:d0:9e:ff:a9:e7:76:ca:6b:4a:
                    92:02:2b:b0:60:bf:18:fc:70:99:36:ef:43:d3:45:
                    28:9a:7f:d9:1c:81:80:1a:92:13:76:d7:bc:c1:c6:
                    3b:d3:33:5f:b3:85:a0:1e:c0:b7:18:77:fc:bd:1e:
                    45:25:39:3c:cd:5f:29:22:d6:88:40:94:59:43:a6:
                    75:cc:ae:24:52:22:e3:eb:99:b8:7b:18:08:07:00:
                    20:63:cb:78:17:4c:16:05:ea:1e:cf:ec:f5:72:64:
                    f7:f6:0c:d8:c2:70:17:5a:1d:8d:d5:8d:fc:7d:3c:
                    56:db:27:3b:04:94:b0:34:ec:18:5b:09:97:7c:bb:
                    53:0e:7e:40:72:06:10:7a:73:cd:4b:49:e1:76:10:
                    55:9f:2a:81:ea:8e:3f:61:3c:3d:3c:16:1c:06:fe:
                    5c:b1:14:a8:52:2d:20:de:d7:7c:aa:a8:c7:61:09:
                    00:22:f9:cd:4a:f2:c8:f2:1d:f7:cf:05:28:7e:37:
                    92:25:ae:a6:a3:a6:61:0d:02:c4:a4:4a:a7:ce:ed:
                    2c:c3
                Exponent: 65537 (0x10001)
        X509v3 extensions:
            X509v3 Basic Constraints: 
                CA:FALSE
            X509v3 Key Usage: critical
                Digital Signature, Non Repudiation, Key Encipherment, Key Agreement
            Netscape Cert Type: 
                SSL Client, S/MIME
            X509v3 Subject Alternative Name: critical
                URI:https://localhost:8443/2013/card#me
            X509v3 Subject Key Identifier: 
                3C:1B:CF:F2:E5:59:9A:E8:76:BE:83:1D:64:FB:07:4E:08:C6:FC:14
    Signature Algorithm: sha1WithRSAEncryption
         07:97:78:f5:11:58:00:50:17:91:14:e8:e3:0d:34:22:74:07:
         ae:61:39:87:23:7a:6c:5c:14:af:13:a6:c8:54:ac:55:d4:41:
         25:45:eb:52:90:ff:56:b0:f9:71:be:ec:c8:2c:a1:19:1c:86:
         42:04:3c:55:7c:96:5c:60:70:0a:d7:ed:5b:53:11:56:7e:14:
         32:92:b9:22:a7:c6:ce:ff:77:17:4a:ac:da:02:ac:24:0e:0e:
         35:18:bd:e3:73:00:3b:8a:aa:ec:86:76:66:dd:4b:1b:da:0c:
         c8:a1:d3:27:26:df:bf:6f:55:11:50:3b:8e:04:12:5a:b9:d4:
         7d:7e
```

We would of course like to make the card world readable so that the certificate
can be used to login to other servers too. To do this the user `<card#me>` can
append to the `<card.acl.ttl>` file a world readable auth

```bash
$ cat ../eg/card.acl.update 
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
INSERT DATA {
[] acl:accessTo <https://localhost:8443/2013/card#me> ;
   acl:mode acl:Read;
   acl:agentClass foaf:Agent .
}
$ curl -X PATCH -k -i -H "Content-Type: application/sparql-update; utf-8"  --cert ../eg/test-localhost.pem:test --data-binary @../eg/card.acl.update https://localhost:8443/2013/card.acl
```

It is now possible to read the card without authentication

```bash
curl -i -k  -H "Accept: text/turtle"  https://localhost:8443/2013/card
HTTP/1.1 200 OK
Link: <https://localhost:8443/2013/card.acl>; rel=acl
Content-Type: text/turtle
Content-Length: 1037


<https://localhost:8443/2013/card#me> <http://xmlns.com/foaf/0.1/name> "Your Name"^^<http://www.w3.org/2001/XMLSchema#string> ;
    <http://xmlns.com/foaf/0.1/knows> <htt........
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
