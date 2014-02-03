


/**
 * Permits to know in which conditions we are using a CORS proxy (if one is configured)
 * @param uri
 */
$rdf.Fetcher.prototype.requiresProxy = function(url) {
    var isCorsProxyConfigured = $rdf.Fetcher.crossSiteProxyTemplate;
    if ( !isCorsProxyConfigured ) {
        return false;
    } else {
        // /!\ this may not work with the original version of RDFLib
        var isUriAlreadyProxified = (url.indexOf($rdf.Fetcher.crossSiteProxyTemplate) == 0);
        var isHomeServerUri = (url.indexOf($rdf.Fetcher.homeServer) == 0)
        if ( isUriAlreadyProxified || isHomeServerUri ) {
            return false;
        } else {
            return true;
        }
    }
}

/**
 * Permits to proxify an url if RDFLib is configured to be used with a CORS Proxy
 * @param url
 * @returns {String} the original url or the proxied url
 */
$rdf.Fetcher.prototype.proxifyIfNeeded = function(url) {
    if ( this.requiresProxy(url) ) {
        return $rdf.Fetcher.crossSiteProxy(url);
    } else {
        return url;
    }
}

$rdf.Fetcher.prototype.proxifySymbolIfNeeded = function(symbol) {
    Preconditions.checkArgument( $rdf.Stmpl.isSymbolNode(symbol),"This is not a symbol!"+symbol);
    var url = $rdf.Stmpl.symbolNodeToUrl(symbol);
    var proxifiedUrl = this.proxifyIfNeeded(url)
    return $rdf.sym(proxifiedUrl);
}







var hardcodedFetcherTimeout = 5000; // in millies, temporary hardcoded

/**
 * return the Promise of a graph fro a given url
 * @param {String} uri to fetch as string (!) (Not a $rdf.sym()). The URI may contain a fragment because it results in a pointedGraph
 * @param {String} referringTerm the url as string (!) referring to the the requested url (Not a $rdf.sym())
 * @param {boolean} force, force fetching of resource even if already in store
 * @return {Promise}  of a pointedGraph
 */
$rdf.Fetcher.prototype.fetch = function(uri, referringTerm, force) {
    var self = this;
    var uriSym = $rdf.sym(uri);
    var docUri = $rdf.Stmpl.fragmentless(uri);
    var docUriSym = $rdf.sym(docUri);
    // The doc uri to fetch is the doc uri that may have been proxyfied
    var docUriToFetch = self.proxifyIfNeeded(docUri);
    var docUriToFetchSym = $rdf.sym(docUriToFetch);
    // if force mode enabled -> we previously unload so that uriFetchState will be "unrequested"
    if ( force ) {
        self.unload(docUriToFetchSym);
    }
    var uriFetchState = self.getState(docUriToFetch);
    // if it was already fetched we return directly the pointed graph pointing
    if (uriFetchState == 'fetched') {
        return Q.fcall(function() {
            return $rdf.pointedGraph(self.store, uriSym, docUriSym, docUriToFetchSym)
        });
    }
    // if it was already fetched and there was an error we do not try again
    // notice you can call "unload(symbol)" if you want a failed request to be fetched again if needed
    else if ( uriFetchState == 'failed') {
        return Q.fcall(function() {
            throw new Error("Previous fetch has failed for"+docUriToFetch+" -> Will try to fetch it again");
        });
    }
    // else maybe a request for this uri is already pending, or maybe we will have to fire a request
    // in both case we are interested in the answer
    else if ( uriFetchState == 'requested' || uriFetchState == 'unrequested' ) {
        if ( uriFetchState == 'requested') {
            // TODO this needs to be tested and may not work well,
            // we may not have already encountered this situation already
            console.error("This code may not work: please tell me if it does when you test it hahaha :)");
            console.info("A request is already being done for",docUriToFetch," -> will wait for that response");
        }
        var deferred = Q.defer();
        self.addCallback('done', function fetchDoneCallback(uriFetched) {
            if ( docUriToFetch == uriFetched ) {
                deferred.resolve($rdf.pointedGraph(self.store, uriSym, docUriSym, docUriToFetchSym));
                return false; // stop
            }
            return true; // continue
        });
        self.addCallback('fail', function fetchFailureCallback(uriFetched, statusString, statusCode) {
            if ( docUriToFetch == uriFetched ) {
                deferred.reject(new Error("Async fetch failure [uri="+uri+"][statusCode="+statusCode+"][reason="+statusString+"]"));
                return false; // stop
            }
            return true; // continue
        });

        if (uriFetchState == 'unrequested') {
            // console.debug("Will try to fetch a document that has not yet been fetched;",docUri);
            var result = self.requestURI(docUriToFetch, referringTerm, force);
            if (result == null) {
                // TODO not sure of the effect of this line. This may cause the promise to be resolved twice no?
                deferred.resolve($rdf.pointedGraph(self.store, uriSym, docUriSym, docUriToFetchSym));
            }
        }
        // See https://github.com/stample/react-foaf/issues/8 -> RDFLib doesn't always fire the "fail" callback :(
        // see https://github.com/linkeddata/rdflib.js/issues/30
        return Q.timeout(deferred.promise, hardcodedFetcherTimeout, "Timeout fired after "+hardcodedFetcherTimeout+" because no response from RDFLib :( (rdflib bug)");
    } else {
        throw new Error("Unknown and unhandled uriFetchState="+uriFetchState+" - for URI="+uri)
    }

}
