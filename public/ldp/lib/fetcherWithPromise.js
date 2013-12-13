/**
 * return the Promise of a graph fro a given url
 * @param uri the url to fetch
 * @param referringTerm the url referrng to the the requested url
 * @return {an Promise of Pointed Graph}
 */
$rdf.Fetcher.prototype.fetch = function(uri, referringTerm, useProxy) {
	var self = this;
	console.log(self.store);
	// Create a promise which create a new PG.
	var promise = new RSVP.Promise(function (resolve, reject) {
			var sta = self.getState(uri);
			if (sta == 'fetched') {
				resolve(new $rdf.PointedGraph(self.store, uri, uri));
			}
			else {
				self.addCallback('done', function (uri2) {
					//todo: use reject on failure and return a pointed graph on the error bnode in the store
					if (uri2 == uri || ( $rdf.Fetcher.crossSiteProxy(uri) == uri2  ))
						resolve(new $rdf.PointedGraph(self.store, uri, uri));
					else {
						console.log("fetch (uri=" + uri + ")==(uri2=" + uri2 + ") is (with proxy verif) false")
					}
				});
				if (sta == 'unrequested') {
				    var newURI = useProxy?$rdf.Fetcher.crossSiteProxy(uri):uri;
					self.requestURI(newURI, uri, useProxy);
				}
			}

		}
	);

	return promise;
}

