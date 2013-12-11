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
	var friends = graph.each(uriSym, FOAF('knows'));

	$.get(templateUriContact, function(temp) {
		// Render each contact.
		var i = 0;
		_.each(friends, function(user) {
			console.log(user);
			renderUserBar(temp, user);
		});
	});
}

function renderUserBar(temp, user) {
	// Show contacts Bar.
	loadScript("/assets/ldp/js/contactViewer.js", function() {
		ContactViewer.initialize(temp, user);
	});
}