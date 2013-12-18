/**
 *
 * @param store Quand Store
 * @param pointer onto an object.  Type : $rdf.sym() - ie Literal, Bnode, or URI
 * @param graphName name of graph in which to point - // Type : $rdf.sym() but limited to URI
 * @return {$rdf.PointedGraph}
 */
$rdf.pointedGraph = function(store, pointer, graphName) {
	return new $rdf.PointedGraph(store, pointer, graphName);
};

$rdf.PointedGraph = function() {
	$rdf.PointedGraph = function(graph, pointer, graphName, useProxy){
		this.graph = graph;
		this.pointer = pointer; //# // Type : $rdf.sym()
		this.graphName = graphName; // Type : $rdf.sym()
		this.useProxy = useProxy || false;
	};
	$rdf.PointedGraph.prototype.constructor = $rdf.PointedGraph;

    // Utils
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

    // relUri => Array[Pgs]
	$rdf.PointedGraph.prototype.rel = function (relUri) {
		console.log("***********$rdf.PointedGraph.prototype.rel*******************");
		var g = this.graph;
		var n = this.graphName;
		var p = this.pointer;
        //console.log("rel !!!!!")

		// Select all triples that matches the predicate relUri.
		var resList = g.statementsMatching(p, relUri, undefined, n, false);

		// Create as much PG as q results.
		var pgList = _.map(resList, function (it) {
			return new $rdf.PointedGraph(g, it.object, n);
		});

		return pgList;
	}

    // Array[relUri] => Array[Pgs]
    $rdf.PointedGraph.prototype.rels = function() {
        var self = this;
        //console.log("rels !!!")

        var pgList = _.chain(arguments)
            .map(function(arg) {
                return self.rel(arg)
            })
            .flatten()
            .value()

        return pgList;
    }

    // relUri => Observable[Pgs]
    $rdf.PointedGraph.prototype.observableRel = function(relUri) {
        console.log("***********$rdf.PointedGraph.prototype.observableRel*******************");
        var self = this;
        //for each pg in pgList
        //if pg is a bnode or a literal or a local URI
        // then return bnode as Obvervable result
        // else
        // fetch remote graph and create a new PG with the right pointer,
        // and return that as an Observable result
        //return observer?

        var pgList = this.rel(relUri);
        var localRemote = _.groupBy(pgList,function(pg){return pg.isLocalPointer()});
        var source1 = (localRemote.true && localRemote.true.length > 0)?
            Rx.Observable.fromArray(localRemote.true):
            Rx.Observable.empty();
        var source2 = Rx.Observable.create(function(observer) {
            _.map(localRemote.false, function(pg) {
                var f = $rdf.fetcher(pg.graph);
                var docURL = pg.pointer.uri.split('#')[0];
                var promise = f.fetch(docURL, pg.graphName, self.useProxy);
                promise.then(
                    function(x){
                        //todo: need to deal with errors
                        console.log("Observable rel => On Next for " + pg.graphName);
                        observer.onNext(new $rdf.PointedGraph(pg.graph,pg.pointer, $rdf.sym(docURL)))
                    },
                    function(err) {
                        console.log("Observable rel => On Error for " + pg.graphName);
                        observer.onError(err)
                    }
                );
            })
        });
        return source1.merge(source2);
    }

	$rdf.PointedGraph.prototype.rev = function (relUri) {
		console.log("***********$rdf.PointedGraph.prototype.rev*******************");
		var g = this.graph;
		var n = this.graphName;
		var p = this.pointer;

		// Select all that matches the relation relUri.
		var resList = g.statementsMatching(undefined, relUri, p, n, false);

		// Create as much PG as q results.
		var pgList = _.map(resList, function (it) {
			return new $rdf.PointedGraph(g, it.subject, n);
		});

		return pgList;
	}

    // relUri => List[Symbol]
    $rdf.PointedGraph.prototype.getSymbol = function() {
        var self = this;

        // List of PGs.
        console.log("getSymbol !!!")
        var pgList = this.rels.apply(this, arguments);
        console.log(pgList)

        //
        var infoTab =
            _.chain(pgList)
                .filter(function (pg) {
                    return (pg.pointer.termType == 'symbol');
                })
                .map(function(pg) {
                    return pg.pointer.value
                })
                .value();
        console.log(infoTab)
        return infoTab
    }

    // relUri => List[Literal]
    $rdf.PointedGraph.prototype.getLiteral = function () {
        var self = this;

        // List of PGs.
        console.log("getLiteral !!!")
        var pgList = this.rels.apply(this, arguments);
        console.log(pgList)

        //
        var infoTab =
            _.chain(pgList)
                .filter(function (pg) {
                    console.log(pg.pointer.termType)
                    return (pg.pointer.termType == 'literal');
                })
                .map(function (pg) {
                    return pg.pointer.value
                })
                .value();
        console.log(infoTab)
        return infoTab;
    }

	$rdf.PointedGraph.prototype.relFirst = function(relUri) {
		var l = $rdf.PointedGraph.prototype.rel(relUri);
		if (l.length > 0) return l[0];
	}

    // Interaction with the PGs.
    $rdf.PointedGraph.prototype.destroy = function () {}

    $rdf.PointedGraph.prototype.delete = function(relUri, value) {
        var queryDelete =
            'PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n' +
            'DELETE DATA \n' +
            '{' + "<" + this.pointer.value + ">" + relUri + ' "' + value + '"' + '. \n' + '}';


        // Sparql request return a promise.
        return sparqlPatch(this.pointer.value, queryDelete);
    }

    $rdf.PointedGraph.prototype.insert = function(relUri, value) {
        var queryInsert =
            'PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n' +
            'INSERT DATA \n' +
            '{' + "<" + this.pointer.value + ">" + relUri + ' "' + value + '"' + '. \n' + '}';

        // Sparql request return a promise.
        return sparqlPatch(this.pointer.value, queryInsert);
    }

    $rdf.PointedGraph.prototype.update = function (relUri, oldvalue, newValue) {
        var queryDeleteInsert =
            'DELETE DATA \n' +
                '{' + "<" + this.pointer.value + "> " + relUri + ' "' + oldvalue + '"' + '} ;\n' +
            'INSERT DATA \n' +
                '{' + "<" + this.pointer.value + "> " + relUri + ' "' + newValue + '"' + '. } ';

        // Sparql request return a promise.
        return sparqlPatch(this.pointer.value, queryDeleteInsert);
    }

    // Future.
    $rdf.PointedGraph.prototype.future = function(pointer, name) {
		$rdf.PointedGraph(this.graph, pointer, this.graphName)
	}

	// Is the symbol defined in the named graph pointed to by this PointedGraph
	$rdf.PointedGraph.prototype.isLocal = function (symbol) {
		var gName = this.graphName;
		if (symbol.termType == 'literal' || symbol.termType == 'bnode') {
			return true
		} else {
			//todo: not perfect ( does not take care of 303 redirects )
			var doc = symbol.uri.split('#')[0];
			return gName && doc == gName.uri;
		}
	}

	$rdf.PointedGraph.prototype.isLocalPointer = function() {
		return this.isLocal(this.pointer)
	}

	$rdf.PointedGraph.prototype.print= function() {
		return "PG(<"+this.pointer+">, <"+this.graphName+"> = { "+
		$rdf.Serializer(this.graph).statementsToN3(this.graph.statementsMatching(undefined, undefined, undefined, this.graphName)) + "}"
	}

	return $rdf.PointedGraph;
}();
