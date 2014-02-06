(function(root, undef) {



///////////////////////////////////////////////////////////////////////////////////////////////
// pg.js, part of rdflib-pg-extension.js made by Stample
// see https://github.com/stample/rdflib.js
///////////////////////////////////////////////////////////////////////////////////////////////

$rdf.PG = {
    createNewStore: function(fetcherTimeout) {
        var store = new $rdf.IndexedFormula();
        // this makes "store.fetcher" variable available
        $rdf.fetcher(store, fetcherTimeout, true);
        return store;
    }
}

$rdf.PG.Utils = {

    /**
     * Just a little helper method to verify preconditions and fail fast.
     * See http://en.wikipedia.org/wiki/Precondition
     * See http://en.wikipedia.org/wiki/Fail-fast
     * @param condition
     * @param message
     */
    checkArgument: function(condition, message) {
        if (!condition) {
            throw Error('IllegalArgumentException: ' + (message || 'No description'));
        }
    },

    /**
     * remove hash from URL - this gets the document location
     * @param url
     * @returns {*}
     */
    fragmentless: function(url) {
        return url.split('#')[0];
    },

    isFragmentless: function(url) {
        return url.indexOf('#') == -1;
    },

    isFragmentlessSymbol: function(node) {
        return this.isSymbolNode(node) && this.isFragmentless(this.symbolNodeToUrl(node));
    },


    getTermType: function(node) {
        if ( node && node.termType ) {
            return node.termType
        } else {
            throw new Error("Can't get termtype on this object. Probably not an RDFlib node: "+node);
        }
    },


    isLiteralNode: function(node) {
        return this.getTermType(node) == 'literal';
    },
    isSymbolNode: function(node) {
        return this.getTermType(node) == 'symbol';
    },
    isBlankNode: function(node) {
        return this.getTermType(node) == 'bnode';
    },

    literalNodeToValue: function(node) {
        this.checkArgument(this.isLiteralNode(node), "Node is not a literal node:"+node);
        return node.value;
    },
    symbolNodeToUrl: function(node) {
        this.checkArgument(this.isSymbolNode(node), "Node is not a symbol node:"+node);
        return node.uri;
    },





    /**
     * Get the nodes for a given relation symbol
     * @param pg
     * @param relSym
     * @returns => List[Nodes]
     */
    getNodes: function(pg, relSym) {
        return _.chain( pg.rels(relSym) )
            .map(function(pg) {
                return pg.pointer;
            }).value();
    },

    getLiteralNodes: function(pg, relSym) {
        return _.chain(pgUtils.getNodes(pg,relSym))
            .filter($rdf.PG.Utils.isLiteralNode)
            .value();
    },
    getSymbolNodes: function(pg, relSym) {
        return _.chain(pgUtils.getNodes(pg,relSym))
            .filter($rdf.PG.Utils.isSymbolNode)
            .value();
    },
    getBlankNodes: function(pg, relSym) {
        return _.chain(pgUtils.getNodes(pg,relSym))
            .filter($rdf.PG.Utils.isBlankNode)
            .value();
    },


    /**
     *
     * @param pgList
     * @returns {*}
     */
    getLiteralValues: function(pgList) {
        var rels = (slice.call(arguments, 1));
        var res =  _.chain(pgList)
            .map(function (pg) {
                return pg.getLiteral(rels);
            })
            .flatten()
            .value();
        return res;
    }

}


$rdf.PG.Filters = {
    isLiteralPointer: function(pg) {
        return pg.isLiteralPointer();
    },
    isBlankNodePointer: function(pg) {
        return pg.isBlankNodePointer();
    },
    isSymbolPointer: function(pg) {
        return pg.isSymbolPointer();
    }
}

$rdf.PG.Transformers = {
    literalPointerToValue: function(pg) {
        return $rdf.PG.Utils.literalNodeToValue(pg.pointer);
    },
    symbolPointerToValue: function(pg) {
        return $rdf.PG.Utils.symbolNodeToUrl(pg.pointer);
    }
}


///////////////////////////////////////////////////////////////////////////////////////////////
// pointedGraph.js, part of rdflib-pg-extension.js made by Stample
// see https://github.com/stample/rdflib.js
///////////////////////////////////////////////////////////////////////////////////////////////

/**
 * A pointed graph is a pointer in a named graph.
 * A named graph is an http resource/document which contains an RDF graph.
 * A pointer is a particular node in this graph.
 *
 * This PointedGraph implementation provides methods to navigate from one node to another in the current namedGraph,
 * but it also permits to jump from one namedGraph to another (firing http requests) if a pointer points to a remote node.
 *
 * @param {$rdf.store} store - Quad Store
 * @param {$rdf.node} pointer: point in the current graph.  Type: Literal, Bnode, or URI
 * @param {$rdf.sym} namedGraphUrl: the URL of the current RDF graph.
 * @return {$rdf.PointedGraph}
 */
$rdf.pointedGraph = function(store, pointer, namedGraphUrl) {
    return new $rdf.PointedGraph(store, pointer, namedGraphUrl);
};


$rdf.PointedGraph = function() {
    $rdf.PointedGraph = function(store, pointer, namedGraphUrl){
        // TODO assert the  pointer is a node
        $rdf.PG.Utils.checkArgument( $rdf.PG.Utils.isFragmentlessSymbol(namedGraphUrl),"The namedGraphUrl should be a fragmentless symbol! -> "+namedGraphUrl);
        this.store = store;
        this.pointer = pointer;
        this.namedGraphUrl = namedGraphUrl;
        // The namedGraphFetchUrl is the namedGraphUrl that may or not be proxified.
        // We need this because we kind of hacked RDFLib and unfortunatly if there's a cors proxy enabled,
        // rdflib will only remember the proxified version of the url in the store
        this.namedGraphFetchUrl = store.fetcher.proxifySymbolIfNeeded(namedGraphUrl);
    };
    $rdf.PointedGraph.prototype.constructor = $rdf.PointedGraph;





    // TODO this logging stuff must be moved somewhere else :(

    // Logs.
    var logLevels = $rdf.PointedGraph.logLevels = {
        nologs: 0,
        debug: 1,
        info: 2,
        warning: 3,
        error: 4
    };

    // Default is no logs.
    $rdf.PointedGraph.logLevel = logLevels.nologs;

    // To change the level of logs
    $rdf.PointedGraph.setLogLevel = function(level) {
        $rdf.PointedGraph.logLevel = (logLevels[level] == null ? logLevels.info : logLevels[level]);
    }

    var doLog = function(level, consoleLogFunction ,messageArray) {
        var loggingEnabled = ($rdf.PointedGraph.logLevel !== logLevels.nologs);
        if ( loggingEnabled ) {
            var shouldLog = ( (logLevels[level] || logLevels.debug) >= $rdf.PointedGraph.logLevel );
            if ( shouldLog ) {
                // TODO maybe it may be cool to prefix the log with the current pg infos
                consoleLogFunction.apply(console,messageArray);
            }
        }
    }

    // Specific functions for each level of logs.
    var debug = function() { doLog('debug', console.debug, _.toArray(arguments)) };
    var info = function() { doLog('info', console.info, _.toArray(arguments)) };
    var warning = function() { doLog('warning', console.warn, _.toArray(arguments)) };
    var error = function() { doLog('error', console.error, _.toArray(arguments)) };








    // Utils.
    function sparqlPatch(uri, query) {
        var promise = $.ajax({
            type: "PATCH",
            url: uri,
            contentType: 'application/sparql-update',
            dataType: 'text',
            processData:false,
            data: query
        }).promise();
        return promise;
    }

    function sparqlPut(uri, query) {
        var promise = $.ajax({
            type: "PUT",
            url: uri,
            contentType: 'application/sparql-update',
            dataType: 'text',
            processData:false,
            data: query
        }).promise();
        return promise;
    }



    /**
     * From the pointer, this follows a predicate/symbol/rel and gives a list of pointer in the same graph/document.
     * @param {$rdf.sym} rel the relation from this node
     * @returns {[PointedGraph]} of PointedGraphs with the same graph name in the same store
     */
    $rdf.PointedGraph.prototype.rel = function (rel) {
        $rdf.PG.Utils.checkArgument( $rdf.PG.Utils.isSymbolNode(rel) , "The argument should be a symbol:"+rel);
        var self = this;
        var resList = this.getCurrentDocumentTriplesMatching(this.pointer, rel, undefined, false);
        return _.map(resList, function (triple) {
            return new $rdf.PointedGraph(self.store, triple.object, self.namedGraphUrl, self.namedGraphFetchUrl);
        });
    }

    /**
     * This is the reverse of "rel": this permits to know which PG in the current graph/document points to the given pointer
     * @param  {$rdf.sym} rel the relation to this node
     * @returns {[PointedGraph]} of PointedGraphs with the same graph name in the same store
     */
    $rdf.PointedGraph.prototype.rev = function (rel) {
        $rdf.PG.Utils.checkArgument( $rdf.PG.Utils.isSymbolNode(rel) , "The argument should be a symbol:"+rel);
        var self = this;
        var resList = this.getCurrentDocumentTriplesMatching(undefined, rel, this.pointer, false);
        return _.map(resList, function (triple) {
            return new $rdf.PointedGraph(self.store, triple.subject, self.namedGraphUrl, self.namedGraphFetchUrl);
        });
    }

    /**
     * Same as "rel" but follow mmultiple predicates/rels
     * @returns {*}
     */
        // Array[relUri] => Array[Pgs]
    $rdf.PointedGraph.prototype.rels = function() {
        var self = this;
        var pgList = _.chain(arguments)
            .map(function(arg) {
                return self.rel(arg)
            })
            .flatten()
            .value()
        return pgList;
    }


    /**
     * This permits to follow a relation in the local graph and then jump asynchronously.
     * This produces a stream of pointed graphs in the form of an RxJs Observable
     * @param Observable[PointedGraph]
     */
    $rdf.PointedGraph.prototype.jumpRelObservable = function(relUri) {
        var self = this;
        var pgList = self.rel(relUri);
        return Rx.Observable.create(function observerFunction(observer) {
            pgList.map(function(pg) {
                pg.jumpAsync().then(
                    function (jumpedPG) {
                        observer.onNext(jumpedPG);
                    },
                    function (jumpError) {
                        // TODO how to handle this correctly? should we send errors with onNext????
                        // can't call onError here because it stops the Observable to work on the first error :(
                        // observer.onError(jumpError);
                        console.warn("jumpRelObservable jumpAsync error",jumpError);
                    }
                )
            });
        });
    }

    /**
     * Nearly the same as jumpAsync except it will not fetch remote document but will only use documents
     * that are already in the store. This means that you can't jump to a remote document that has not been previously
     * loaded in the store or an error will be thrown.
     * @returns {$rdf.PointedGraph}
     */
    $rdf.PointedGraph.prototype.jump = function() {
        if ( this.isLocalPointer() ) {
            return this;
        }
        else {
            var pointerDocumentUrl = this.getSymbolPointerDocumentUrl();
            var pointerDocumentFetchUrl = this.store.fetcher.proxifyIfNeeded(pointerDocumentUrl);
            var uriFetchState = this.store.fetcher.getState(pointerDocumentFetchUrl);
            if (uriFetchState == 'fetched') {
                return $rdf.pointedGraph(this.store, this.pointer, $rdf.sym(pointerDocumentUrl), $rdf.sym(pointerDocumentFetchUrl) );
            } else {
                // If this error bothers you, you may need to use jumpAsync
                throw new Error("Can't jump because the jump requires ["+pointerDocumentUrl+"] to be already fetched." +
                    " This resource is not in the store. State="+uriFetchState);
            }
        }
    }


    /**
     * This permits to jump to the pointer document if the document
     * This will return the current PG if the pointer is local (bnode/literal/local symbols...)
     * This will return a new PG if the pointer refers to another document.
     *
     * So, basically
     * - (documentUrl - documentUrl#hash ) will return (documentUrl - documentUrl#hash )
     * - (documentUrl - documentUrl2#hash ) will return (documentUrl2 - documentUrl2#hash )
     *
     * @returns {Promise[PointedGraph]}
     */
    $rdf.PointedGraph.prototype.jumpAsync = function() {
        var originalPG = this;
        if ( originalPG.isLocalPointer() ) {
            return Q.fcall(function () {
                return originalPG;
            })
        }
        else {
            return this.jumpFetchRemote();
        }
    }

    /**
     * This permits to follow a remote symbol pointer and fetch the remote document.
     * This will give you a PG with the same pointer but the underlying document will be
     * the remote document instead of the current document.
     *
     * For exemple, let's suppose:
     * - current PG (documentUrl,pointer) is (url1, url1#profile)
     * - current document contains triple (url1#profile - foaf:knows - url2#profile)
     * - you follow the foaf:knows rel and get PG2 (url1, url2#profile)
     * - then you can jumpFetch on PG2 because url2 != url1
     * - this will give you PG3 (url2, url2#profile)
     * - you'll have the same pointer, but the document is different
     *
     * @returns {Promise[PointedGraph]}
     */
    $rdf.PointedGraph.prototype.jumpFetchRemote = function() {
        $rdf.PG.Utils.checkArgument( this.isRemotePointer(),"You are not supposed to jumpFetch if you already have all the data locally. Pointer="+this.pointer);
        var pointerUrl = this.getSymbolPointerUrl();
        var referrerUrl = $rdf.PG.Utils.symbolNodeToUrl(this.namedGraphUrl);
        var force = false;
        return this.store.fetcher.fetch(pointerUrl, referrerUrl, force);
    }



    // relUri => List[Symbol]
    $rdf.PointedGraph.prototype.getSymbol = function() {
        var rels = _.flatten(arguments); // TODO: WTF WHY DO WE NEED TO FLATTEN!!!
        var pgList = this.rels.apply(this, rels);
        var symbolValueList =
            _.chain(pgList)
                .filter($rdf.PG.Filters.isSymbolPointer)
                .map($rdf.PG.Transformers.symbolPointerToValue)
                .value();
        return symbolValueList
    }

    // relUri => List[Literal]
    // TODO change the name
    $rdf.PointedGraph.prototype.getLiteral = function () {
        var rels = _.flatten(arguments);  // TODO: WTF WHY DO WE NEED TO FLATTEN!!!
        var pgList = this.rels.apply(this, rels);
        var literalValueList = _.chain(pgList)
            .filter($rdf.PG.Filters.isLiteralPointer)
            .map($rdf.PG.Transformers.literalPointerToValue)
            .value();
        return literalValueList;
    }

    $rdf.PointedGraph.prototype.relFirst = function(relUri) {
        var l = this.rel(relUri);
        if (l.length > 0) return l[0];
    }

    // Interaction with the PGs.
    $rdf.PointedGraph.prototype.delete = function(relUri, value) {
        var query =
            'PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n' +
                'DELETE DATA \n' +
                '{' + "<" + this.pointer.value + ">" + relUri + ' "' + value + '"' + '. \n' + '}';


        // Sparql request return a promise.
        return sparqlPatch(this.pointer.value, query);
    }

    $rdf.PointedGraph.prototype.insert = function(relUri, value) {
        var query =
            'PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n' +
                'INSERT DATA \n' +
                '{' + "<" + this.pointer.value + ">" + relUri + ' "' + value + '"' + '. \n' + '}';

        // Sparql request return a promise.
        return sparqlPatch(this.pointer.value, query);
    }

    $rdf.PointedGraph.prototype.update = function (relUri, newValue, oldvalue) {
        var query =
            'DELETE DATA \n' +
                '{' + "<" + this.pointer.value + "> " + relUri + ' "' + oldvalue + '"' + '} ;\n' +
                'INSERT DATA \n' +
                '{' + "<" + this.pointer.value + "> " + relUri + ' "' + newValue + '"' + '. } ';

        // Sparql request return a promise.
        return sparqlPatch(this.pointer.value, query);
    }

    $rdf.PointedGraph.prototype.updateStore = function(relUri, newValue) {
        this.store.removeMany(this.pointer, relUri, undefined, this.namedGraphFetchUrl);
        this.store.add(this.pointer, relUri, newValue, this.namedGraphFetchUrl);
    }

    $rdf.PointedGraph.prototype.replaceStatements = function(pg) {
        var self = this;
        this.store.removeMany(undefined, undefined, undefined, pg.namedGraphFetchUrl);
        _.each(pg.store.statements, function(stat) {
            self.store.add(stat.subject, stat.predicate, stat.object, pg.namedGraphFetchUrl)
        });
    }

    $rdf.PointedGraph.prototype.ajaxPut = function (baseUri, data, success, error, done) {
        $.ajax({
            type: "PUT",
            url: baseUri,
            dataType: "text",
            contentType: "text/turtle",
            processData: false,
            data: data,
            success: function (data, status, xhr) {
                if (success) success(xhr)
            },
            error: function (xhr, status, err) {
                if (error) error(xhr)
            }
        })
            .done(function () {
                if (done) done()
            });
    }


    $rdf.PointedGraph.prototype.print = function() {
        return this.printSummary() + " = { "+this.printContent() + "}"
    }
    $rdf.PointedGraph.prototype.printSummary = function() {
        return "PG[pointer="+this.pointer+" - NamedGraph="+this.namedGraphUrl+"]";
    }
    $rdf.PointedGraph.prototype.printContent = function() {
        return $rdf.Serializer(this.store).statementsToN3(this.store.statementsMatching(undefined, undefined, undefined, this.namedGraphFetchUrl));
    }
    $rdf.PointedGraph.prototype.toString = function() {
        return this.printSummary();
    }

    /**
     * Return a clone of the current pointed graph in another store.
     * This is useful to edit a pointed graph.
     * Once the edit is validated it may be nice to merge the small temporary edited store
     * to the original big store.
     */
    // TODO need better name
    $rdf.PointedGraph.prototype.deepCopyOfGraph = function() {
        var self = this;
        var triples = this.store.statementsMatching(undefined, undefined, undefined, this.namedGraphFetchUrl);
        var store = new $rdf.IndexedFormula();
        $rdf.fetcher(store, 100000, true); // TODO; deals with timeOut
        _.each(triples, function(stat) {
            store.add(stat.subject, stat.predicate, stat.object, self.namedGraphFetchUrl)
        });
        return new $rdf.PointedGraph(store, this.pointer, this.namedGraphUrl, this.namedGraphFetchUrl);
    }


    $rdf.PointedGraph.prototype.isSymbolPointer = function() {
        return $rdf.PG.Utils.isSymbolNode(this.pointer);
    }
    $rdf.PointedGraph.prototype.isLiteralPointer = function() {
        return $rdf.PG.Utils.isLiteralNode(this.pointer);
    }
    $rdf.PointedGraph.prototype.isBlankNodePointer = function() {
        return $rdf.PG.Utils.isBlankNode(this.pointer);
    }

    /**
     * Returns the Url of the pointer.
     * The url may contain a fragment.
     * Will fail if the pointer is not a symbol because you can't get an url for a blank node or a literal.
     */
    $rdf.PointedGraph.prototype.getSymbolPointerUrl = function() {
        return $rdf.PG.Utils.symbolNodeToUrl(this.pointer);
    }

    /**
     * Returns the Url of the document in which points the symbol pointer.
     * The url is a document URL so it won't contain a fragment.
     * Will fail if the pointer is not a symbol because you can't get an url for a blank node or a literal.
     */
    $rdf.PointedGraph.prototype.getSymbolPointerDocumentUrl = function() {
        var pointerUrl = this.getSymbolPointerUrl();
        return $rdf.PG.Utils.fragmentless(pointerUrl);
    }


    /**
     * Returns the current document/namedGraph Url (so it has no fragment)
     */
    $rdf.PointedGraph.prototype.getCurrentDocumentUrl = function() {
        return $rdf.PG.Utils.symbolNodeToUrl(this.namedGraphUrl);
    }

    /**
     * This permits to find triples in the current document.
     * This will not look in the whole store but will only check in the current document/namedGraph
     * @param pointer (node)
     * @param rel (node)
     * @param object (node)
     * @param onlyOne: set true if you only want one triple result (for perf reasons for exemple)
     * @returns {*}
     */
    $rdf.PointedGraph.prototype.getCurrentDocumentTriplesMatching = function (pointer,rel,object,onlyOne) {
        // In the actual version it seems that RDFLib use the fetched url as the "why"
        // Maybe it's because we have modified it a little bit to work better with our cors proxy.
        // This is why we need to pass the namedGraphFetchUrl and not the namedGraphUrl
        var why = this.namedGraphFetchUrl;
        return this.store.statementsMatching(pointer, rel, object, why, onlyOne);
    }

    /**
     * This permits to find the triples that matches a given rel/predicate and object
     * for the current pointer in the current document.
     * @param rel
     * @param object
     * @param onlyOne
     */
    $rdf.PointedGraph.prototype.getPointerTriplesMatching = function(rel,object,onlyOne) {
        return this.getCurrentDocumentTriplesMatching(this.pointer, rel, object, onlyOne);
    }

    /**
     * Returns the Url of the currently pointed document.
     * Most of the time it will return the current document url.
     * It will return a different url only for non-local symbol nodes.
     *
     * If you follow a foaf:knows, you will probably get a list of PGs where the pointer document
     * URL is not local because your friends will likely describe themselves in different resources.
     */
    $rdf.PointedGraph.prototype.getPointerDocumentUrl = function() {
        if ( this.isSymbolPointer() ) {
            return this.getSymbolPointerDocumentUrl();
        } else {
            return this.getCurrentDocumentUrl();
        }
    }

    /**
     * Permits to know if the pointer is local to the current document.
     * This will be the case for blank nodes, literals and local symbol pointers.
     * @returns {boolean}
     */
    $rdf.PointedGraph.prototype.isLocalPointer = function() {
        return this.getPointerDocumentUrl() == this.getCurrentDocumentUrl();
    }
    $rdf.PointedGraph.prototype.isRemotePointer = function() {
        return !this.isLocalPointer();
    }

    /**
     * Permits to "move" to another subject in the given graph
     * @param newPointer
     * @returns {$rdf.PointedGraph}
     */
    $rdf.PointedGraph.prototype.withPointer = function(newPointer) {
        return new $rdf.PointedGraph(this.store, newPointer, this.namedGraphUrl, this.namedGraphFetchUrl);
    }

    /**
     * Permits to know if the given pointer have at least one rel that can be followed.
     * This means that the current pointer exists in the local graph as a subject in at least one triple.
     */
    $rdf.PointedGraph.prototype.hasRels = function() {
        return this.getCurrentDocumentTriplesMatching(this.pointer, undefined, undefined, true).length > 0;
    }

    /**
     * Permits to know if the given pointer have at least one rev that can be followed.
     * This means that the current pointer exists in the local graph as an object in at least one triple.
     */
    $rdf.PointedGraph.prototype.hasRevs = function() {
        return this.getCurrentDocumentTriplesMatching(undefined, undefined, this.pointer, true).length > 0;
    }


    return $rdf.PointedGraph;
}();


///////////////////////////////////////////////////////////////////////////////////////////////
// fetcherWithPromise.js, part of rdflib-pg-extension.js made by Stample
// see https://github.com/stample/rdflib.js
///////////////////////////////////////////////////////////////////////////////////////////////

/*
TODO:
this proxification code is kind of duplicate of RDFLib's "crossSiteProxyTemplate" code.
How can we make this code be integrated in rdflib nicely?
*/


/**
 * Permits to know in which conditions we are using a CORS proxy (if one is configured)
 * @param uri
 */
$rdf.Fetcher.prototype.requiresProxy = function(url) {
    var isCorsProxyConfigured = $rdf.Fetcher.fetcherWithPromiseCrossSiteProxyTemplate;
    if ( !isCorsProxyConfigured ) {
        return false;
    }
    else {
        // /!\ this may not work with the original version of RDFLib
        var isUriAlreadyProxified = (url.indexOf($rdf.Fetcher.fetcherWithPromiseCrossSiteProxyTemplate) == 0);
        var isHomeServerUri = (url.indexOf($rdf.Fetcher.homeServer) == 0)
        if ( isUriAlreadyProxified || isHomeServerUri ) {
            return false;
        } else {
            return true;
        }
    }
}


/**
 * permits to proxify the URI
 * @param uri
 * @returns {string}
 */
$rdf.Fetcher.prototype.proxify = function(uri) {
    if ( uri && uri.indexOf('#') != -1 ) {
        throw new Error("Tit is forbiden to proxify an uri with a fragment:"+uri);
    }
    if ( uri && uri.indexOf($rdf.Fetcher.fetcherWithPromiseCrossSiteProxyTemplate) == 0 ) {
        throw new Error("You are trying to proxify an URL that seems to already been proxified!"+uri);
    }
    return $rdf.Fetcher.fetcherWithPromiseCrossSiteProxyTemplate + encodeURIComponent(uri);
};

/**
 * Permits to proxify an url if RDFLib is configured to be used with a CORS Proxy
 * @param url
 * @returns {String} the original url or the proxied url
 */
$rdf.Fetcher.prototype.proxifyIfNeeded = function(url) {
    if ( this.requiresProxy(url) ) {
        return this.proxify(url);
    } else {
        return url;
    }
}

$rdf.Fetcher.prototype.proxifySymbolIfNeeded = function(symbol) {
    $rdf.PG.Utils.checkArgument( $rdf.PG.Utils.isSymbolNode(symbol),"This is not a symbol!"+symbol);
    var url = $rdf.PG.Utils.symbolNodeToUrl(symbol);
    var proxifiedUrl = this.proxifyIfNeeded(url);
    return $rdf.sym(proxifiedUrl);
}






/**
 * Return the Promise of a pointed graph for a given url
 * @param {String} uri to fetch as string. The URI may contain a fragment because it results in a pointedGraph
 * @param {String} referringTerm the uri as string. Referring to the requested url
 * @param {boolean} force, force fetching of resource even if already in store
 * @return {Promise} of a pointedGraph
 */
$rdf.Fetcher.prototype.fetch = function(uri, referringTerm, force) {
    var self = this;
    var uriSym = $rdf.sym(uri);
    var docUri = $rdf.PG.Utils.fragmentless(uri);
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
        self.addCallback('fail', function fetchFailureCallback(uriFetched, statusString, xhr) {
            if ( docUriToFetch == uriFetched ) {
                deferred.reject(new Error("Async fetch failure [uri="+uri+"][statusCode="+xhr.status+"][reason="+statusString+"]"));
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
        return deferred.promise;
    }
    else {
        throw new Error("Unknown and unhandled uriFetchState="+uriFetchState+" - for URI="+uri)
    }

}})(this);
