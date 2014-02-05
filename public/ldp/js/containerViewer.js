var AppStarter = {
	initialize: function(pointedGraph) {
		var self = this;
		var templateUri = "/assets/ldp/templates/containerTemplate.html";

		// Load necessary CSS and Scripts files
		this.loadVariousFiles();

		// Get current user relative URI.
		this.pointedGraph = pointedGraph;
		this.baseGraph = pointedGraph.store;
		this.baseUri = pointedGraph.namedGraphUrl.uri;

		$.get(templateUri, function(template) {
			loadScript("/assets/ldp/js/menuViewer.js", function() {
				// Create menu.
				MenuView.initialize()

				// Set template.
				self.template = template;

				// render.
				self.render();
			});
		})
	},

	// Load Css and utils files.
	loadVariousFiles: function() {
		// Load related CSS.
		loadCSS("/assets/ldp/css/blueprint.css");
		loadCSS("/assets/ldp/css/common.css");
		loadCSS("/assets/ldp/css/font-awesome.min.css");
		loadCSS("/assets/ldp/css/buttons.css");
		loadCSS("/assets/ldp/css/style.css");

		// Load utils js.
        loadScript("/assets/ldp/lib/rdflib.js", null);
		loadScript("/assets/ldp/js/utils.js", null);
		loadScript("/assets/ldp/js/utils/appUtils.js", null);
	},

	// Get content of the current container and render them.
	getContainerContent: function() {
		var self = this;
		var template2URI = "/assets/ldp/templates/containerEltTemplate.html";

		// Fetch template for container items.
		$.get(template2URI, function (template) {
			var onResult, onDone;

			// Define Sparql query to get items in container.
			var sparqlQuery =
				"PREFIX ldp: <http://www.w3.org/ns/ldp#> \n" +
					"PREFIX stat:  <http://www.w3.org/ns/posix/stat#> \n" +
					"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
					"SELECT ?m ?type ?size ?mt \n" +
					"WHERE {\n" +
					" <" + self.baseUri + "> ldp:created ?m . \n" +
					" OPTIONAL { ?m stat:size ?size . } \n" +
					" OPTIONAL { ?m a ?type . } \n" +
					" OPTIONAL { ?m stat:mtime ?mt .} \n" +
					"}"

			// Bind the Sparql query to the graph.
			var fileQuery = $rdf.SPARQLToQuery(sparqlQuery, false, self.baseGraph);

			// Define callback for Sparql request.
			onResult = function (result) {
				// Create corresponding contact view.
				loadScript("/assets/ldp/js/containerEltView.js", function() {
					containerEltView.initialize(result, template);
				});
			};
			onDone = function (result) {
				console.log('done');
			};

			// Execute the Sparql query.
			self.baseGraph.query(fileQuery, onResult, undefined, onDone);
		});
	},

	// Render.
	render: function() {
		// Load Html.
		var template = _.template(this.template, {});

		// Append in the DOM.
		$("body").append(template);

		// Render each items in the container.
		this.getContainerContent();
	}
};


/*
var templateURI = "/assets/ldp/templates/containersTemplate.html";
var template2URI = "/assets/ldp/templates/containerTemplate.html";
var tab = {};
$.get(templateURI, function (data) {
	// Load Html.
	var template = _.template(data, tab);

	// Append in the DOM.
	$("#viewerContent").append(template);

	// Fetch template for container items.
	$.get(template2URI, function (data) {
		var onResult, onDone;
		var templateAll = "";
		var $lines = $('.lines');
		var LDP = $rdf.Namespace("http://www.w3.org/ns/ldp#");

		// Get base graph and uri.
		var baseUri = baseUriGlobal;
		var baseGraph = graphsCache[baseUri];

		// Define Sparql query.
		var sparqlQuery =
			"PREFIX ldp: <http://www.w3.org/ns/ldp#> \n" +
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
				informations.type = (result['?type'].value == ldp("Container") ) ? "Container" : "-"
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
			} catch (error) {
				informations.mtime = "-";
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
			$lines.find("a[class = 'accessControl']").bind('click', function (e) {
				var container = $(e.target).parent().parent().parent();
				$rdf.ressourceUri = container.find('.filename a').attr("href");

				// Open ACL Viewer.
				loadScript("/assets/ldp/js/aclEditorViewer.js", null);
			});

			// Delete Ressource.
			$lines.find("a[class='deleteFile']").bind('click', function (e) {
				var container = $(e.target).parent().parent().parent();
				var uri = container.find('.filename a').attr("href");
				var success = function () {
					container.remove();
				};
				var error = function () {
					//window.location.reload();
				};
				deleteRessource(uri, success, error, null);
			});
		};

		// Execute the query.
		baseGraph.query(fileQuery, onResult, undefined, onDone);
	});


}, 'html');
*/