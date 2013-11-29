/**
 * Copyright (c) 2012 Henry Story
 *
 * Licensed under the MIT license:
 *   http://www.opensource.org/licenses/mit-license.php
 */


// For quick access to namespace often used in foaf profiles
var FOAF = $rdf.Namespace("http://xmlns.com/foaf/0.1/"),
	RDF = $rdf.Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
	RDFS = $rdf.Namespace("http://www.w3.org/2000/01/rdf-schema#"),
	OWL = $rdf.Namespace("http://www.w3.org/2002/07/owl#"),
	DC = $rdf.Namespace("http://purl.org/dc/elements/1.1/"),
	RSS = $rdf.Namespace("http://purl.org/rss/1.0/"),
	XSD = $rdf.Namespace("http://www.w3.org/TR/2004/REC-xmlschema-2-20041028/#dt-"),
	CONTACT = $rdf.Namespace("http://www.w3.org/2000/10/swap/pim/contact#"),

	graphsCache = {},
	panelsCache = [],
	spinner = undefined,
	spinnerOptions = {
		lines: 9, // The number of lines to draw
		length: 5, // The length of each line
		width: 2, // The line thickness
		radius: 6, // The radius of the inner circle
		corners: 1, // Corner roundness (0..1)
		rotate: 0, // The rotation offset
		direction: 1, // 1: clockwise, -1: counterclockwise
		color: '#FFF', // #rgb or #rrggbb
		speed: 0.5, // Rounds per second
		trail: 60, // Afterglow percentage
		shadow: false, // Whether to render a shadow
		hwaccel: false, // Whether to use hardware acceleration
		className: 'spinner', // The CSS class to assign to the spinner
		zIndex: 2e9, // The z-index (defaults to 2000000000)
		top: 'auto', // Top position relative to parent in px
		left: 'auto' // Left position relative to parent in px
	};


//$rdf.Fetcher.crossSiteProxyTemplate = "http://data.fm/proxy?uri={uri}";
$rdf.Fetcher.crossSiteProxyTemplate = "http://localhost:9000/srv/cors?url={uri}";



/**
 * Render the card for the user as described in the graph/knowledge base knowledgeBase
 */
function renderFetchedUserCard(fetchedUserUrl, fetchedUserGraph) {

	var $user_wrapper = $("#user_wrapper"),
		newCard = $user_wrapper.clone(),
		address = undefined,
		$address_container = newCard.find(".address"),
		$address = newCard.find(".address .user_info_input");



	/** Mailboxes in FOAF are usually written as <mailto:henry.story@bblfish.net> . This
	 * function removes the 'mailto:' part, if it exists */
	function removeProtocol(uri) {
		if (uri && uri.indexOf(":") >= 1) {
			var parts = uri.split(":");
			return parts[1];
		}
		else return uri
	}

	// Define a SPARQL query to fetch the address
	var sparqlQuery = "PREFIX contact:  <http://www.w3.org/2000/10/swap/pim/contact#> \n" +
			"SELECT ?city ?country ?postcode ?street \n" +
			"WHERE {\n" +
			"  " + fetchedUserUrl + " contact:home ?h . \n" +
			"  ?h contact:address ?addr  . \n" +
			"  OPTIONAL { ?addr contact:city ?city . } \n" +
			"  OPTIONAL { ?addr contact:country ?country . } \n" +
			"  OPTIONAL { ?addr contact:postalCode ?postcode . } \n" +
			"  OPTIONAL { ?addr contact:street ?street . } \n" +
			"}",

	// Build the address query JS object
	addressQuery = $rdf.SPARQLToQuery(sparqlQuery, false, fetchedUserGraph);

	// Place address in HTML
	var onResult = function (result) {

		$address_container.show();

		// Helper that checks if element exists and returns it formatted
		function ifE(name) {
			var res = result[name];
			if (res) return res + "<br>";
			else return " "
		}

		// Compose and fill in the address
		address = ifE("?street") + ifE("?postcode") + ifE("?city") + ifE("?country");
		$address.html(address);
	};

	// Hide address field if no address
	var onDone = function () {

		if (!address) {
			$address_container.hide();
		} else {
			$address_container.show();
		}

	};

	// Query the fetched graph
	fetchedUserGraph.query(addressQuery, onResult, undefined, onDone);

	// Helper to select the first existing element of a series of arguments
	var findFirst = function () {
		var obj = undefined,
			i = 0;
		while (!obj && i < arguments.length) {
			obj = fetchedUserGraph.any(fetchedUserUrl, arguments[i++]);
		}
		return obj
	}

	// Add image if available
	var img = findFirst(FOAF('img'), FOAF('depiction'));
	var c = newCard.find(".depiction");
	if (img) {
		c.attr("src", img.uri);
		c.show();
	}
	else c.hide();

	// Add name
	var nam = findFirst(FOAF('name'));
	if (!nam) {
		nam = "Name Not Defined"
	}
	else nam = nam.value;
	newCard.find(".name").text(nam);

	// Add nickname
	var nick = findFirst(FOAF('nick'));
	if (!nick) {
		nick = ""
	} else nick = nick.value;
	newCard.find(".nickname").text(nick);

	// Add email if available
	var mbox = fetchedUserGraph.any(fetchedUserUrl, FOAF('mbox'));
	var email_container = newCard.find(".email");
	var email_el = newCard.find(".email .user_info_input");
	if (mbox) {
		email_container.show();
		email_el.text(removeProtocol(mbox.uri));
		email_el.attr("href", mbox.uri)
	} else {
		email_container.hide();
	}

	// Add phone if available
	var phone = fetchedUserGraph.any(fetchedUserUrl, FOAF('phone'));
	var phone_container = newCard.find(".phone");
	var phone_el = newCard.find(".phone .user_info_input");
	if (phone) {
		phone_container.show();
		phone_el.text(removeProtocol(phone.uri));
		phone_el.attr("href", phone.uri)
	} else {
		phone_container.hide()
	}

	// Add website if available
	var website = fetchedUserGraph.any(fetchedUserUrl, FOAF('homepage'));
	var website_container = newCard.find(".website");
	var website_el = newCard.find(".website .user_info_input");
	if (website) {
		website_container.show();
		website_el.html("<a href='" + website.uri + "'>" + website.uri + "</a>");
	} else {
		website_container.hide();
	}

	// Add bday if available
	var bday = fetchedUserGraph.any(fetchedUserUrl, FOAF('birthday'));
	var bday_container = newCard.find(".birthday");
	var bday_el = newCard.find(".birthday .user_info_input");
	if (bday) {
		bday_container.show();
		bday_el.text(bday.value);
	} else {
		bday_container.hide();
	}

	// Place new card in DOM
	$user_wrapper.replaceWith(newCard);

	// Hide spinner
	spinner.stop();
	$(spinner.el).hide();
	spinner = undefined;
}


// TestStore implementation from dig.csail.mit.edu/2005/ajar/ajaw/test/rdf/rdfparser.test.html
// RDFIndexedFormula from dig.csail.mit.edu/2005/ajar/ajaw/rdf/identity.js
//  (extends RDFFormula from dig.csail.mit.edu/2005/ajar/ajaw/rdf/term.js which has no indexing and smushing)
// for the real implementation used by Tabulator which uses indexing and smushing

// var kb = new TestStore()


/**
 * Fill out the html template for the person identified by the WebID person as described in the knowledge
 * base kb, in column col of the explorer
 */
function displayFriends(person, graph, col, depth) {

	var panel = col < 4 ? "#panel" + col : "#panel3",
		friends = graph.each(person, FOAF('knows')),
		i,
		n = friends.length,
		friend,
		list = "";

	// Build the friend list and store it
	for (i = 0; i < n; i++) {
		friend = friends[i];
		if (friend && friend.termType === 'symbol') { //only show people with a WebID for the moment.
			var name = graph.any(friend, FOAF('name'));
			if (!name) {
				name = friend.uri
			}
			list += "<a class='listing_user' href='" + friend.uri + "'>" + name + "<br>"
		}
	}
	panelsCache[depth] = list;

	// Empty unneeded panels
	for (i = col + 1; i <= 3; i++) {
		$("#panel" + i).empty()
	}

	// Moving up in tree when needs shifting panels
	if (col == 2 && depth > 1) {

		$("#panel1").html(panelsCache[depth-2]);
		$("#panel1 .listing_user").unbind().bind('click', function (e) {
			e.preventDefault();
			loadUser($(this).attr("href"), 2, depth-1);
		});

		$("#panel2").html(panelsCache[depth-1]);
		$("#panel2 .listing_user").unbind().bind('click', function (e) {
			e.preventDefault();
			loadUser($(this).attr("href"), 3, depth);
		});

		$("#panel3").html(list);
		$("#panel3 .listing_user").unbind().bind('click', function (e) {
			e.preventDefault();
			loadUser($(this).attr("href"), 4, depth+1);
		});

	}

	// Moving in tree without shifting panels
	else if (col < 3) {
		$(panel).html(list);
	}

	// Moving down in tree when needs shifting panels
	else {

		$("#panel1").html(panelsCache[depth-2]);
		$("#panel1 .listing_user").unbind().bind('click', function (e) {
			e.preventDefault();
			loadUser($(this).attr("href"), 2, depth -1);
		});

		$("#panel2").html(panelsCache[depth-1]);
		$("#panel2 .listing_user").unbind().bind('click', function (e) {
			e.preventDefault();
			loadUser($(this).attr("href"), 3, depth);
		});

		$("#panel3").html(list);

	}

	// Bind action to click on user
	$(panel + " .listing_user").unbind().bind('click', function (e) {
		e.preventDefault();
		loadUser($(this).attr("href"), col + 1, depth + 1);
	});

}

/**
 *  Update the screen when the selected user identified by webid is pressed in
 *  explorer column col
 */

function loadUser(webId, col, depth) {


	// Display spinner
	if (spinner) {
		spinner.stop();
		$(spinner.el).hide();
	}
	spinner = new Spinner(spinnerOptions).spin();
	$("body").append(spinner.el);

	// Initialize
	if (!col) col = 1;
	if (!depth) depth = 0;

	// Look for hashbang in URL and remove it and anything after it
	var person = $rdf.sym(webId),
		indexOf = webId.indexOf('#'),
		docURI;
	if (indexOf >= 0)
		docURI = webId.slice(0, indexOf);
	else  docURI = webId;

	// If the knowledge base was not initialised fetch info from the web (if need CORS go through CORS proxy)
	var graph = graphsCache[docURI];
	if (!graph) {
		graph = graphsCache[docURI] = new $rdf.IndexedFormula();
		var fetch = $rdf.fetcher(graph);
		fetch.nowOrWhenFetched(docURI, undefined, function () {
			renderFetchedUserCard(person, graph);
			displayFriends(person, graph, col, depth);

			// Display the loaded card
			$("#user_wrapper").show();

		});
	} else {  //this does not take into account ageing!
		renderFetchedUserCard(person, graph);
		displayFriends(person, graph, col, depth);

		// Display the loaded card
		$("#user_wrapper").show();
	}


}






