/**
 * return the Promise of a graph fro a given url
 * @param url to fetch as string (!) (Not a $rdf.sym())
 * @param referringTerm the url as string (!) referring to the the requested url (Not a $rdf.sym())
 * @return {an Promise of Pointed Graph}
 */
$rdf.Fetcher.prototype.fetch = function(uri, referringTerm, useProxy) {
	var self = this;
	console.log(self.store);
	// Create a promise which create a new PG.
	var promise = new RSVP.Promise(function (resolve, reject) {
			var sta = self.getState(uri);
			if (sta == 'fetched') {
				resolve(new $rdf.PointedGraph(self.store, $rdf.sym(uri), $rdf.sym(uri)));
			}
			else {
				self.addCallback('done', function (uri2) {
					console.log('done');
					//todo: use reject on failure and return a pointed graph on the error bnode in the store
					if (uri2 == uri || ( $rdf.Fetcher.crossSiteProxy(uri) == uri2  ))
						resolve(new $rdf.PointedGraph(self.store, $rdf.sym(uri), $rdf.sym(uri)));
					else {
						console.log("fetch (uri=" + uri + ")==(uri2=" + uri2 + ") is (with proxy verif) false")
					}
				});
				if (sta == 'unrequested') {
				    var newURI = useProxy? $rdf.Fetcher.crossSiteProxy(uri):uri;
					self.requestURI(newURI, uri, useProxy);
				}
			}

		}
	);

	return promise;
}

