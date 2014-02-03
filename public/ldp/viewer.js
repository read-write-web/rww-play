(function() {
    var RDF = $rdf.Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    var STAMPLE = $rdf.Namespace("http://ont.stample.co/2013/display#");

    // Global variables.
    var storeGlobal = new $rdf.IndexedFormula();
    var graphUri = window.location.href;
    var rdfTypeToViewerUri = window.location.origin + "/assets/ldp/viewer.ttl";
    $rdf.fetcher(storeGlobal, 10000, true);

    // Define promises for graph and router.
    var promiseGraph = storeGlobal.fetcher.fetch(graphUri);
    var promiseRdfTypeToViewer = storeGlobal.fetcher.fetch(rdfTypeToViewerUri);

    Q.all([promiseGraph, promiseRdfTypeToViewer])
        .spread(function(graphPg, viewerGraphPg) {
            console.log(graphPg)
            console.log(viewerGraphPg)
            var viewerList = _.chain(graphPg.rel(RDF("type")))
                .map(function(pg){
                    return viewerGraphPg.getCurrentDocumentTriplesMatching(pg.pointer, STAMPLE("view"), undefined);
                })
                .flatten()
                .map(function(stat) {
                    return stat.object;
                }).value( ) ;

            // Load viewer.
            if (viewerList && viewerList.length > 0) {
                var viewerJsUri = viewerList[0].uri ;
                // Load viewer app.
                loadScript(viewerJsUri, function() {
                    App.initialize (graphPg);
                });
            }
            else { throw new Error ( 'no viewer can render any of the RDF document types: ' + viewerList ) ; }
        });
}).call(this)
