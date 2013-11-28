console.log('View apps !!!');

var templateURI = "/assets/ldp/templates/appsTemplate.html";
var tab = {};
$.get(templateURI,
	function(data) {
	// Load Html.
	var template = _.template(data, tab);

	// Append in the DOM.
	$('.cloudactions').append(template);

	// Get base graph and uri.
	var baseUri = $rdf.baseUri;
	var baseGraph = $rdf.graphsCache[baseUri];

	// Which apps it is ?
	var apps = baseGraph.statementsMatching(undefined,
		RDF('type'),
		WEBAPP('app'),
		$rdf.sym(baseUri));
	if (apps.length <= 0) {
		// TODO: deal with this!
	}

	var app = apps[0]['subject'];
	var name = baseGraph.any(app, WEBAPP('name'));

	// Load the relative app.
	if (name.value === "LD-Cal") {
		// Load the menu.
		loadScript("/assets/ld-cal/contrib/fullcalendar.min.js");
		loadScript("/assets/ld-cal/js/ld-cal.js");
		loadScript("/assets/ldp/js/ldcalViewer.js", null);
	}
},
	'html');
