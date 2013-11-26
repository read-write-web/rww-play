console.log("Container view");

// Load useful js.
loadScript("/assets/ldp/js/deleteRessource.js", null);

var templateURI = "/assets/ldp/templates/containerTemplate.html";
$.get(templateURI, function(data) {
	var onResult, onDone;
	var templateAll = "";
	var $lines = $('.lines');
	var LDP = $rdf.Namespace("http://www.w3.org/ns/ldp#");

	// Get base graph and uri.
	var baseUri = $rdf.baseUri;
	var baseGraph = $rdf.graphsCache[baseUri];

	// Define Sparql query.
	var sparqlQuery =
		"PREFIX ldp: <http://www.w3.org/ns/ldp#> \n"+
			"PREFIX stat:  <http://www.w3.org/ns/posix/stat#> \n" +
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
			"SELECT ?m ?type ?size ?mt \n" +
			"WHERE {\n" +
			" <" + baseUri + "> ldp:created ?m . \n" +
			" OPTIONAL { ?m stat:size ?size . } \n" +
			" OPTIONAL { ?m a ?type . } \n" +
			" OPTIONAL { ?m stat:mtime ?mt .} \n" +
			"}"

	// Bind the query to the graph.
	var fileQuery = $rdf.SPARQLToQuery(sparqlQuery, false, baseGraph);

	// ...
	onResult = function (result) {
		console.log('OnResult');

		// Save ressource informations.
		var informations = {};
		informations.uri = result['?m'].uri;

        try {
           informations.name = basename(result['?m'].uri.toString())
        } catch (error) {
            informations.name = "!!**JS ERROR**!!"
        }

		// Get the type.
		try {
			informations.type =  (result['?type'].value == ldp("Container") )?"Container":"-"
		} catch (error) {
			informations.type = "-";
		}

		// Get the size.
		try {
			informations.size = result['?size'].value;
		} catch (error) {
			informations.size = "-";
		}

		// Get the modification time.
		try {
			informations.mtime = formatTime(result['?mt'].value);
		} catch(error) {
			informations.mtime = "-" ;
		}

		// Load and fill related templates.
		var template = _.template(data, informations);
		templateAll += template;
	};

	// Callback.
	onDone = function (result) {
		console.log('DONE');

		// Append all templates in DOM.
		$lines.append(templateAll);

		// Bind events to view elements.
		// Control ACL: load related editor.
		$lines.find("a[class = 'accessControl']").bind('click', function(e) {
			var container = $(e.target).parent().parent().parent();
			$rdf.ressourceUri = container.find('.filename a').attr("href");
			loadScript("/assets/ldp/js/aclEditorViewer.js", null);
		});

		// Delete Ressource.
		$lines.find("a[class='deleteFile']").bind('click', function(e) {
			var container = $(e.target).parent().parent().parent();
			var uri = container.find('.filename a').attr("href");
			var success = function() {
				container.remove();
			};
			var error = function() {
				//window.location.reload();
			};
			deleteRessource(uri, success, error, null);
		});
	};

	// Execute the query.
	baseGraph.query(fileQuery, onResult, undefined, onDone);

}, 'html');
