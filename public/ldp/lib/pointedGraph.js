/*
*
* */

$rdf.pointedGraph = function(graph, pointer, graphName) {
	return new $rdf.PointedGraph(graph, pointer, graphName);
};

$rdf.PointedGraph = function() {
	$rdf.PointedGraph = function(graph, pointer, graphName){
		this.pointer = pointer;
		this.graph = graph;
		this.graphName = graphName;
	};

	$rdf.PointedGraph.prototype.constructor = $rdf.PointedGraph;

	$rdf.PointedGraph.prototype.rev = function (relUri) {
		console.log("***********$rdf.PointedGraph.prototype.rev*******************");
		var g = this.graph;
		var n = this.graphName;
		var p = this.pointer;

		// Select all that matches the relation relUri.
		var resList = g.statementsMatching(undefined, relUri, p, n, false);
		console.log(resList);

		// Create as much PG as q results.
		var pgList = _.map(resList, function (it) {
			return new $rdf.PointedGraph(g, it.subject, n);
		});

		return pgList;
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

	$rdf.PointedGraph.prototype.observableRel = function(relUri) {
		var self = this;
		//for each pg in pgList
		//if pg is a bnode or a literal or a local URI
		// then return bnode as Obvervable result
		// else
		// fetch remote graph and create a new PG with the right pointer,
		// and return that as an Observable result
		//return observer?

		var fetch = function(pg) {
			var f = $rdf.fetcher(pg.graph);
			f.nowOrWhenFetch(pg.pointer, function(){

			})
		}

		var pgList = this.rel(relUri);
		var localRemote = _.groupBy(pgList,function(pg){return pg.isLocalPointer()});
		console.log("Local Remote true :" + _.size(localRemote.true))
		console.log("Local Remote false :" + _.size(localRemote.false))
		var source1 = Rx.Observable.fromArray(localRemote.true);
		var source2 = Rx.Observable.create(function(observer) {
			_.map(localRemote.false, function(pg) {
				var f = $rdf.fetcher(pg.graph);
				var docURL = pg.pointer.uri.split('#')[0]
				f.nowOrWhenFetched(docURL, pg.graphName, function(){
					//todo: need to deal with errors
					observer.onNext(new $rdf.PointedGraph(pg.graph,pg.pointer,docURL))
				})
			})
		});

		return source1.merge(source2);
	}

	$rdf.PointedGraph.prototype.rel = function (relUri) {
		console.log("***********$rdf.PointedGraph.prototype.rel*******************");
		var g = this.graph;
		var n = this.graphName;
		var p = this.pointer;

		// Select all that matches the relation relUri.
		var resList = g.statementsMatching(p, relUri, undefined, n, false);
		console.log(resList);

		// Create as much PG as q results.
		var pgList = _.map(resList, function (it) {
			return new $rdf.PointedGraph(g, it.object, n);
		});

		return pgList;
	}


	$rdf.PointedGraph.prototype.relFirst = function(relUri) {
		var l = $rdf.PointedGraph.prototype.rel(relUri);
		if (l.length > 0) return l[0];
	}


	$rdf.PointedGraph.prototype.future = function(pointer, name) {
		$rdf.PointedGraph(this.graph, pointer, this.graphName)
	}

	return $rdf.PointedGraph;
}();
