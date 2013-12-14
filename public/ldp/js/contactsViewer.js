var ContactsView = {
	initialize: function(pointedGraph) {
		var self = this;
		var templateUri = "/assets/ldp/templates/contactsBarTemplate.html";
		this.templateUriContact = "/assets/ldp/templates/contactBarTemplate.html";
		console.log('initialise ContactsView');
		console.log(pointedGraph);
		// Set view variables.
		this.pointedGraph = pointedGraph;

		// Fetch template and render.
		$.get(templateUri, function(template) {
			// Set template.
			self.template = template;

			// render.
			self.render();
		})
	},

	getUserContacts:function () {
		var self = this;
		console.log("getUserContacts");

		$.get(this.templateUriContact, function (temp) {
			// Create Observables on user contacts.
			var source = self.pointedGraph.observableRel(FOAF('knows'));
			var subscription = source.subscribe(
				function (pg) {
					console.log("onNext : " + pg.isLocalPointer());
					self.renderUserBar(pg, temp);
				},
				function (err) {
					console.log("onError : ");
					console.log(err);
				},
				function () {
					console.log('Completed !!!')
				}
			)
		});
	},

	renderUserBar: function (pg, temp) {
		console.log('render each');
		// Show contacts Bar.
		loadScript("/assets/ldp/js/contactViewer.js", function () {
			ContactViewer.initialize(pg, temp);
		});
	},

	render: function() {
		var self = this;
		console.log("render");
		// Define template
		var template = _.template(this.template, {});

		// Append to the DOM
		$("#userbar")
			.append(template)
			.show();

		//
		this.getUserContacts();
	}
};













/*
var templateUri = "/assets/ldp/templates/contactsBarTemplate.html";
var templateUriContact = "/assets/ldp/templates/contactBarTemplate.html";
var tab = {};
$.get(templateUri, function(data) {
	// Define template
	var template = _.template(data, tab);

	// Append to the DOM
	$("#userbar")
		.append(template)
		.show();

	console.log(window.location.href);
	console.log(graphsCache);

	// If user graph already fetched, get attributes and render, otherwise fetch it.
	var graph = graphsCache[baseUriGlobal];
	console.log(graph);
	if (!graph) {
		graph = graphsCache[baseUriGlobal] = new $rdf.IndexedFormula();
		var fetch = $rdf.fetcher(graph);
		fetch.nowOrWhenFetched(baseUriGlobal, undefined, function () {
			getUserContacts(graph, $rdf.sym(currentUserGlobal),
				function() {
					if (callback) callback();
				});
		});
	}
	else {
		getUserContacts(graph, $rdf.sym(currentUserGlobal),
			function() {
				if (callback) callback();
			});
	}

});

function getUserContacts(graph, uriSym, callback) {
	console.log("getUserContacts");
	console.log(uriSym);
	*/
/*var friends = graph.each(uriSym, FOAF('knows'));

	$.get(templateUriContact, function(temp) {
		// Render each contact.
		var i = 0;
		_.each(friends, function(user) {
			console.log(user);
			renderUserBar(temp, user);
		});
	});*//*


	$.get(templateUriContact, function(temp) {
	// Create Observables on user contacts.
	var source = pointedGraphGlobal.observableRel(FOAF('knows'));
	var subscription = source.subscribe(
		function(pg) {
			console.log("onNext : " + pg.isLocalPointer());
			//updateAttributesPg(value) ;
			renderUserBar(pg, temp);
		},
		function(err) {
			console.log("onError : " );
			console.log( err.message);
		},
		function() {
			console.log('Completed !!!')
		}
	)
	});
}

function renderUserBar(pg, temp) {
	// Show contacts Bar.
	loadScript("/assets/ldp/js/contactViewer.js", function() {
		ContactViewer.initialize(pg, temp);
	});
}*/
