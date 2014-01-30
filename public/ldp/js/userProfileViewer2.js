// Define templates.
var templateURI = "/assets/ldp/templates/userProfileTemplate.html";
var formTemplate =
	'<form id="form">' +
		'<input id="input" style="">' +
		'<div class="savePrompt">Save changes?' +
		'<span class="yesSave submit">Yes</span>' +
		'<span class="noCancel cancel">No</span>' +
		'</div>'+
		'</form>';



// Default attributes of the user.
var tab = {
	"name":"test",
	"imgUrl":"/assets/ldp/images/user_background.png",
	"nickname":"-",
	"email":"-",
	"phone":"-",
	"city":"-",
	"country":"-",
	"postalCode":"-",
	"street":"-",
	"birthday":"-",
	"gender":"-",
	"website":"-"
};

// Get base graph and uri.
var baseUri = baseUri;
var baseGraph = graphsCache[baseUri];

// Get the template.
$.get(templateURI, function(data) {
	// Get current user relative URI.
	currentUserGlobal = getPersonToDisplay(baseGraph).value;
	console.log("Person to display:" + currentUserGlobal);

	// Load current user.
	loadUser(currentUserGlobal, function() {
		// Define view template.
		var template = _.template(data, tab);

		// Append in the DOM.
		$('#viewerContent').append(template);

		// Bind click for on view elements.
		$(".editLinkImg").on("click", handlerClickOnEditLink);
		$("#friends").on("click", handlerClickOnfriends);
	});
});

function getSubjectsOfType(g,subjectType) {
	var triples = g.statementsMatching(undefined,
		RDF('type'),
		subjectType,
		$rdf.sym(baseUri));
	return _.map(triples,function(triple) { return triple['subject']; })
}

function getFoafPrimaryTopic(g) {
	return g.any($rdf.sym(baseUri),FOAF('primaryTopic'),undefined)
}

function getPersonToDisplay(g) {
	var foafPrimaryTopic = getFoafPrimaryTopic(g);
	if ( foafPrimaryTopic ) {
		return foafPrimaryTopic;
	} else {
		var personUris = getSubjectsOfType(baseGraph,FOAF("Person"));
		if ( personUris.length === 0 ) {
			throw "No person to display in this card: " + baseUri;
		} else {
			return personUris[0];
		}
	}
}


/*
 * Define handlers.
 * */
function handlerClickOnEditLink(e) {
	console.log("handlerClickOnEditLink");
	var parent, parentId , $labelText, $editLink, $form, $input, formerValue;

	// Get target.
	parent = $(e.target).parent().parent();
	parentId = parent.attr('id');

	// Hide name and Open form editor.
	$labelText = parent.find(".labelText");
	$editLink = parent.find(".editLink");
	formerValue = $labelText.html();
	$labelText.hide();
	$editLink.hide();

	// Insert template in the DOM.
	$(formTemplate).insertAfter(parent.find(".labelContainer"));

	// Store useful references.
	$form = parent.find("#form");
	$input = parent.find("#input");

	// Give the form the focus.
	$input
		.val(formerValue)
		.show()
		.focus();

	parent.find(".submit").on("click", function() {
		console.log('submit !');

		// Get the new value.
		var newValue = $input.val();

		// Make the SPARQL request.
		var pred = ' foaf:' + parentId;
		var queryDelete =
			'PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n' +
				'DELETE DATA \n' +
				'{'+
				"<"+ baseUri+"#me" +">" + pred + ' "' + formerValue + '"' + '. \n' +
				'}';

		var queryInsert =
			'PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n' +
				'INSERT DATA \n' +
				'{'+
				"<"+ baseUri+"#me" +">" + pred + ' "' + newValue + '"' + '. \n' +
				'}';

		// Callback success.
		function successCallback() {
			console.log('Saved!');
			// Change value in label.
			$labelText.html(newValue);

			// Re-initiliaze the form.
			$form.remove();
			$labelText.show();
			$editLink.show();
		}

		// Callback error.
		function errorCallback() {
			console.log('error!');
			// Re-initiliaze the form.
			$form.remove();
			$labelText.show();
			$editLink.show();
		}

		// Delete old and insert new triples.
		sparqlPatch(baseUri, queryDelete,
			function() {
				sparqlPatch(baseUri, queryInsert, successCallback, errorCallback);
			},
			function() {
				if (errorCallback) errorCallback();
			}
		);

	});
	parent.find(".cancel").on("click", function() {
		console.log('cancel');
		$form.remove();
		$labelText.show();
		$editLink.show();
	});
}

function handlerClickOnfriends(e) {
	// Show contacts Bar.
	loadScript("/assets/ldp/js/contactsViewer.js", null);
}


/*
 * Clean URI.
 * i.e. : look for hashbang in URL and remove it and anything after it
 * */
function cleanUri(uri) {
	var docURI, indexOf = uri.indexOf('#');
	if (indexOf >= 0)
		docURI = uri.slice(0, indexOf);
	else  docURI = uri;
	return docURI;
}


/*
 * Load a given user (get attributes and render).
 * */
function loadUser(webId, callback) {
	console.log("loadUser");
	console.log(webId);
	// Turn Uri into symbol.
	var uriSym = $rdf.sym(webId);

	// Clean Uri.
	var uriCleaned = cleanUri(webId);

	// If user graph already fetched, get attributes and render, otherwise fetch it.
	var graph = graphsCache[uriCleaned];
	if (!graph) {
		graph = graphsCache[uriCleaned] = new $rdf.IndexedFormula();
		var fetch = $rdf.fetcher(graph);
		fetch.nowOrWhenFetched(uriCleaned, undefined, function () {
			getUserAttributes(graph, uriSym,
				function() {
					if (callback) callback();
				});
		});
	}
	else {
		getUserAttributes(graph, uriSym,
			function() {
				if (callback) callback();
			});
	}
}

/*
 * Clean URI.
 * i.e. : look for hashbang in URL and remove it and anything after it
 * */
function cleanUri(uri) {
	var docURI, indexOf = uri.indexOf('#');
	if (indexOf >= 0)
		docURI = uri.slice(0, indexOf);
	else  docURI = uri;
	return docURI;
}

/**
 * Get attributes of given user by querrying the graph.
 */
function getUserAttributes(graph, uriSym, callback) {
	console.log("getUserAttributes");
	// Helper to select the first existing element of a series of arguments
	var findFirst = function () {
		var obj = undefined,
			i = 0;
		while (!obj && i < arguments.length) {
			obj = graph.any(uriSym, arguments[i++]);
		}
		return obj
	};

	// Add image if available
	var img = findFirst(FOAF('img'), FOAF('depiction'));
    tab.imgUrl=img.uri;

	// Add name
	var nam = findFirst(FOAF('name'));
	tab.name=nam.value;

	// Add nickname
	var nick = findFirst(FOAF('nick'));
	tab.nickname=nick.value;

	// Add email if available
	var email = graph.any(uriSym, FOAF('mbox'));
	tab.email=email.value;

	// Add phone if available
	var phone = graph.any(uriSym, FOAF('phone'));
	tab.phone=phone.value;

	// Add website if available
	var website = graph.any(uriSym, FOAF('homepage'));
	tab.website=website.value;

	// Add bday if available
	var gender = graph.any(uriSym, FOAF('gender'));
	tab.gender=gender.value;

	// Add bday if available
	var bday = graph.any(uriSym, FOAF('birthday'));
	tab.birthday=bday.value;

	/*
	 * Get Contact.
	 * */

	// Define a SPARQL query to fetch the address
	var sparqlQuery = "PREFIX contact:  <http://www.w3.org/2000/10/swap/pim/contact#> \n" +
			"SELECT ?city ?country ?postcode ?street \n" +
			"WHERE {\n" +
			"  " + uriSym + " contact:home ?h . \n" +
			"  ?h contact:address ?addr  . \n" +
			"  OPTIONAL { ?addr contact:city ?city . } \n" +
			"  OPTIONAL { ?addr contact:country ?country . } \n" +
			"  OPTIONAL { ?addr contact:postalCode ?postcode . } \n" +
			"  OPTIONAL { ?addr contact:street ?street . } \n" +
			"}",

	// Build the address query JS object
		addressQuery = $rdf.SPARQLToQuery(sparqlQuery, false, graph);

	// Place address in HTML
	var onResult = function (result) {
		// Fill in the address in tab.
		if (result["?city"]) {tab.city=result["?city"].value;}
		if (result["?country"]) {tab.country=result["?country"].value;}
		if (result["?street"]) {tab.street=result["?street"].value;}
		if (result["?postcode"]) {tab.postalCode=result["?postcode"].value;}
	};

	// Hide address field if no address
	var onDone = function () {
		if (callback) callback();
	};

	// Query the fetched graph
	graph.query(addressQuery, onResult, undefined, onDone);
}