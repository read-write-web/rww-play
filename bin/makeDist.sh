# go to Play root
export PLAY_VERSION=
export PLAY_HOME=
cd $PLAY_HOME/framework
./build clean publish-local create-dist
cd target/dist/
mv play-2.2-TLS play-$PLAY_VERSION
tar cvfj play-$PLAY_VERSION.tar.bz2 play-$PLAY_VERSION
scp play-$PLAY_VERSION.tar.bz2 hjs@bblfish.net:
