FROM ubuntu:14.10

# Usage:
# docker build -t rww-play .
# docker run -it --net=host rww-play
# curl -k https://localhost:8443/2013/card

RUN apt-get update \
	&& apt-get install -yq \
		git \
		openjdk-8-jdk \
		openjdk-8-jre \
	&& rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/read-write-web/rww-play

RUN cd rww-play && ./activator compile

EXPOSE 8443

CMD cd rww-play && ./activator run -Dhttps.port=8443 -Dhttps.trustStore=noCA -Dakka.loglevel=DEBUG -Dakka.debug.receive=on -Drww.root.container.path=test_www
