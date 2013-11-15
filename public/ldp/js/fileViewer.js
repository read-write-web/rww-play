console.log("File view");
var templateURI = "https://localhost:8443/assets/ldp/templates/fileTemplate.html";
var tab = {"fileContent":"Empty File !"};
$.get(templateURI, function(data) {
	var $lines = $('.lines');

	// Get base graph and uri.
	var baseUri = $rdf.baseUri;
	var baseGraph = $rdf.graphsCache[baseUri];
	var baseGraphString = baseGraph.toString();

	// Load and fill related templates.
	var tab = (baseGraphString) ?
		{"fileContent": baseGraphString}:
		{"fileContent":"Empty File !"};

	// Load template with data.
	var template = _.template(data, tab);

	// Append template in DOM.
	$lines.append(template);

}, 'html');
