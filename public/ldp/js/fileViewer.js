console.log("Container view");
var templateURI = "https://localhost:8443/assets/ldp/templates/fileTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
	var onResult, onDone;
	var templateAll = "";
	var $lines = $('.lines');
	var LDP = $rdf.Namespace("http://www.w3.org/ns/ldp#");

	// Get base graph and uri.
	var baseUri = $rdf.baseUri;
	var baseGraph = $rdf.graphsCache[baseUri];

	// Load and fill related templates.
	var template = _.template(data, tab);
	templateAll += template;
	// Append all templates in DOM.
	$lines.append(templateAll);
}, 'html');
