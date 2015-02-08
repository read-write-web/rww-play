rww-play 
========

This is an implementation in Play of a number of tools to build a Read-Write-Web server using Play2.x and akka.
It is very early stages at present and it implements sketches of the following

* A [CORS](http://www.w3.org/TR/cors/) proxy
* An initial implementation of [Linked Data Basic Profile](http://www.w3.org/2012/ldp/wiki/Main_Page)

This currently works in the [2.3.x-TLS branch of the fork of Play 2.x](https://github.com/read-write-web/Play20), which comes with TLS support and a few more patches.

We use [Travis CI](http://travis-ci.org/) to verify the build: [![Build Status](https://travis-ci.org/read-write-web/rww-play.png)](http://travis-ci.org/read-write-web/rww-play)

Community
=========

This project is moving fast. If you have any problems don't hesitate to 
participate, ask questions, whoever small your problem seems to be. There
are no stupid questions!

* Chat with us on [![Gitter chat channel](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/read-write-web?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
* Join the [co-operating-systems mailing list on google groups](https://groups.google.com/forum/?fromgroups#!forum/co-operating-systems)
* Submit issues and requests on the  [issue tracker](rww-play/issues) 

Getting going
=============

* You need at least Java 8 which is [being widely adopted by the Scala community](http://www.infoq.com/news/2014/12/Typesafe-surveys-Java-Adoption)
* clone [this project](https://github.com/stample/rww-play) 
* run
```bash
 $ git clone git://github.com/read-write-web/rww-play.git 
``` 


### Running in single user mode

To start Play in secure mode with lightweight client certificate verification (for WebID); that is, a self-signed certificate:

```bash
 $ ./activator
 [RWWeb] $ idea with-sources=yes	// if you want to run intelliJ
 [RWWeb] $ eclipse with-source=true	// if you want to run eclipse Scala IDE
 [RWWeb] $ compile
 [RWWeb] $ run -Dhttps.port=8443 -Dhttps.trustStore=noCA -Dakka.loglevel=DEBUG -Dakka.debug.receive=on -Drww.root.container.path=test_www 
 ```
Then you can direct your browser to:
[https://localhost:8443/2013/](https://localhost:8443/2013/)


### Running with multiple users securely

If you want to have multiple users on your server, it is best to give each user a subdomain for JS security.
This will protect his space and his javascript from potentially dangerous javascript code uploaded by others.

#### network config

To start Play in secure mode with lightweight client certificate verification (for WebID)
In file:
conf/application.conf
set the smtp parameters: host= and user=
of your mail provider server.

On Unix in `/etc/hosts` add host names for the subdomains you will create, e.g. :
```
127.0.0.1 jmv.localhost
127.0.0.1 jmv1.localhost
127.0.0.1 jmv2.localhost
```

Installing RWW apps
===================

(untested in dev branch as of this commit)

The RWW apps are stored in other git repositories.
One can run the script `./install-app.sh` to install or update the RWW apps that we ship with the platform.
Check the script content, it is simply a git clone. ( If installing on a public server make sure the proxy
url is set. )
 
For subdomains on your local machine you will need to edit `/etc/hosts` for each server. For
machines on the web you can just assign all domains to the same ip address.

```bash
[RWWeb] $ run -Dhttps.port=8443 -Dhttps.trustStore=noCA -Drww.subdomains=true -Dhttp.hostname=localhost -Drww.subdomains=true -Dsmtp.password=secret
```

You can the create yourself a subdomain by pointing your browser to the root domain:
[https://localhost:8443/](https://localhost:8443/). This will lead you to the account creation 
page, which will allow you to create subdomains on your server. An e-mail will be sent to 
your e-mail address for verification ( but you will be able to find the link in the logs 
if the e-mail server is not set up). 


Documentation
=============

Further documentation can be found on the [rww-play wiki](https://github.com/stample/rww-play/wiki).

Licence
=======

   Copyright 2013-2014 Henry Story

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
   
   [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
