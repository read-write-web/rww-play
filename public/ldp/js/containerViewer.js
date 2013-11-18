console.log("Container view");

// Load useful js.
loadScript("https://localhost:8443/assets/ldp/js/deleteRessource.js", null);

var templateURI = "https://localhost:8443/assets/ldp/templates/containerTemplate.html";
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
			"SELECT ?type ?size ?mt \n" +
			"WHERE {\n" +
			"<" + baseUri + "> ldp:created ?m . \n" +
			"OPTIONAL { ?m stat:size ?size . } \n" +
			"OPTIONAL { ?m a ?type . } \n" +
			"OPTIONAL { ?m stat:mtime ?mt .} \n" +
			"}"

	// Bind the query to the graph.
	var fileQuery = $rdf.SPARQLToQuery(sparqlQuery, false, baseGraph);

	// ...
	onResult = function (result) {
		console.log('OnResult');
		console.log(result);
		// Save ressource informations.
		var informations = {};
		informations.uri = result['?m'].uri;

		// Get the type.
		try {
			informations.type =  (result['?type'].value == ldp("Container") )?"Container":"-"
		} catch (error) {
			informations.type = "-";
		}

		// Get the name from uri.
		informations.name = basename(result['?m'].uri);
		if (isDirectory(informations.uri))
			informations.name = informations.name + "/";

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

	// ...
	onDone = function (result) {
		console.log('DONE');
		console.log(result);
		// Append all templates in DOM.
		$lines.append(templateAll);

		/*/ Bind click event to load its content.
		$lines.find("a[href='" + result['?m'].uri +"']").bind('click', function(e) {
			clickDir(e, result['?m'].uri);
		});*/

		$lines.find("a[class = 'accessControl']").bind('click', function(e) {
			console.log('Manage ACL');
		});

		$lines.find("a[class='deleteFile']").bind('click', function(e) {
			console.log('Delete');
			var container = $(e.target).parent().parent().parent();
			var uri = $(e.target).parent().parent().parent().find('.filename a').attr("href");
			console.log(uri);
			var success = function() {
				console.log('sucess');
				container.remove();
			};
			var error = function() {
				console.log('error');
				//window.location.reload();
			};
			deleteRessource(uri, success, error, null);
		});

	};

	// Execute the query.
	baseGraph.query(fileQuery, onResult, undefined, onDone);

	// Get basename.
	var basename = function (path) {
		if (path.substring(path.length - 1) == '/')
			path = path.substring(0, path.length - 1);

		var a = path.split('/');
		return a[a.length - 1];
	};

	// Format from Unix time.
	var formatTime = function(mtime) {
		var a = new Date(mtime*1);
		var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
		var year = a.getFullYear();
		var month = months[a.getMonth()];
		var date = a.getDate();
		var hour = a.getHours();
		var min = a.getMinutes();
		var sec = a.getSeconds();
		var time = year+'-'+month+'-'+date+' '+hour+':'+min+':'+sec + " GMT";
		return time;
	};

	// Check if uri point to a directory.
	var isDirectory = function(uri) {
		var res;
		res = (uri.substring(uri.length - 1) == '/')? true: false;
		return res;
	};

}, 'html');
