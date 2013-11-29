function loadUserApps() {
	var g = $rdf.graph();
	var f = $rdf.fetcher(g);
	// add CORS proxy
	//$rdf.Fetcher.crossSiteProxyTemplate=PROXY;

	var webid = "https://localhost:8443/2013/card#me";
	var docURI = webid.slice(0, webid.indexOf('#'));
	var webidRes = g.sym(webid);
	console.log("loadUserApps");
	console.log(docURI);
	// fetch user data
	f.nowOrWhenFetched(docURI, undefined, function() {
		console.log(new $rdf.Serializer(g).toN3(g));
		var apps = g.statementsMatching(
			undefined,
			$rdf.sym('http://www.w3.org/1999/02/22-rdf-syntax-ns#type'),
			WEBAPP('app'),
			$rdf.sym(docURI)
		);

		if (apps.length > 0) {
			_.each(apps, function(items) {
				var app = items['subject'];
				var service = g.any(app,  WEBAPP('serviceId'));
				var endpoint = g.any(app,  WEBAPP('endpoint'));
				var name = g.any(app,  WEBAPP('name'));
				console.log(service);
				// check if the user registered the app
				if ((service) && (service.value === "https://apps.localhost:8443/ld-cal/")) {
					console.log(endpoint.value);
					storageURI = endpoint.value;
					loadRemote(storageURI);
					return true;
				}
			});
		}
		else {
			//???
		}
	});

}