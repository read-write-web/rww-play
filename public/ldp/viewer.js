(function() {

    var RDF = $rdf.Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    var STAMPLE = $rdf.Namespace("http://ont.stample.co/2013/display#");
    var FOAF = $rdf.Namespace("http://xmlns.com/foaf/0.1/");

    var documentUrl = window.location.href.split('#')[0]; // Href without fragment
    var viewerMappingPgUrl = window.location.origin + "/assets/ldp/viewer.ttl";


    function createStore() {
        var store = new $rdf.IndexedFormula();
        $rdf.fetcher(store, 10000, true);
        return store;
    }

    /**
     * From the viewerMappingGraph, permits to know which viewer can be used to render which resource.
     * Return a list of potential js apps URL.
     * @param viewerMappingPg
     * @param rdfTypeSymbol
     * @returns {*}
     */
    function getViewerCandidates(viewerMappingPg,rdfTypeSymbol) {
        var triples = viewerMappingPg.getCurrentDocumentTriplesMatching(rdfTypeSymbol, STAMPLE("view"), undefined);
        return _.chain(triples)
            .map(function(triple) {
                return triple.object.uri;
            }).value();
    }

    /**
     * Get the url of the JS app to use to render the given pg
     * @param viewerMappingPg
     * @param pgToRender
     */
    function selectViewerAppUrl(viewerMappingPg,pgToRender) {
        var rdfTypePg = pgToRender.relFirst( RDF("type") );
        if ( rdfTypePg ) {
            var rdfType = rdfTypePg.pointer;
            var viewerCandidates = getViewerCandidates(viewerMappingPg,rdfType);
            if (viewerCandidates && viewerCandidates.length > 0) {
                var viewerUrl = viewerCandidates[0];
                if ( viewerCandidates.length > 1 ) {
                    console.warn("Multiple viewers",viewerCandidates,"are able to render this kind of document",rdfType," Will use the first one: ",viewerUrl);
                }
                return viewerUrl;
            }
            else {
                throw new Error ("no viewer can render the given rdf type: "+rdfType + " for pg="+pgToRender.printSummary());
            }
        } else {
            throw new Error("The pg to render has no type: "+pgToRender.printSummary());
        }
    }


    /**
     * Permits to know which Pg the viewer app will have to render
     * @param documentPg
     */
    function getPgToRender(documentPg) {
        // TODO the logic here must be enhanced,
        // maybe using the window.location.hash ?
        var primaryTopicPg = documentPg.relFirst( FOAF("primaryTopic") );
        if ( primaryTopicPg ) {
            return primaryTopicPg;
        } else  {
            return documentPg;
        }
    }

    function doRenderInViewer(viewerAppUrl,pgToRender) {
        loadScript(viewerAppUrl, function() {
            console.info("Will initialize app",viewerAppUrl, " with ",pgToRender.printSummary());
            AppStarter.initialize(pgToRender);
        });
    }


    var store = createStore();
    var documentPgPromise = store.fetcher.fetch(documentUrl);
    var viewerMappingPgPromise = store.fetcher.fetch(viewerMappingPgUrl);

    Q.all( [documentPgPromise, viewerMappingPgPromise] )
        .spread(function(documentPg, viewerMappingPg) {
            var pgToRender = getPgToRender(documentPg);
            var viewerAppUrl = selectViewerAppUrl(viewerMappingPg,pgToRender);
            doRenderInViewer(viewerAppUrl,pgToRender);
        });

}).call(this)
