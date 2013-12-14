var BodyView = {
	initialize: function(viewerJsUri, pointedGraph) {
		var self = this;
		var templateURI = "/assets/ldp/templates/bodyTemplate.html";
		console.log('initialize Appview');

		// Load necessary CSS and Scripts files
		this.loadVariousFiles();

		// Load the template.
		$.get(templateURI, function(template) {
			// Set the template.
			self.template = template;

			// Render.
			self.render();

			// Load viewer app.
			loadScript(viewerJsUri, function() {
				App.initialize(pointedGraph);
			});
		});

		// Bind events to DOM elements.
		this.bindEventsToDom();
	},

	removeView: function(){

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
		loadScript("/assets/ldp/js/utils.js", null);
		loadScript("/assets/ldp/js/utils/appUtils.js", null);
	},

	bindEventsToDom: function() {},

	bindEventsToView: function() {},

	// Render.
	render: function() {
		console.log('render Appview');

		// Define template.
		var html = _.template(this.template, {});

		// Append to DOM.
		$('body').append(html);

		// Bind events to view elements.
		this.bindEventsToView();
	}
};










/*
var templateURI = "/assets/ldp/templates/bodyTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
	// Define proxy.
	$rdf.Fetcher.crossSiteProxyTemplate = "http://data.fm/proxy?uri={uri}";
	//$rdf.Fetcher.crossSiteProxyTemplate = window.location.origin+"/srv/cors?url={uri}";

    // Load related CSS.
	loadCSS("/assets/ldp/css/blueprint.css");
    loadCSS("/assets/ldp/css/common.css");
    loadCSS("/assets/ldp/css/font-awesome.min.css");
    loadCSS("/assets/ldp/css/buttons.css");
	loadCSS("/assets/ldp/css/style.css");

	// Load utils js.
	loadScript("/assets/ldp/js/utils.js", null);
	loadScript("/assets/ldp/js/utils/appUtils.js", null);

    // Load Html template.
    var template = _.template(data, tab);

	// Append in the DOM.
	$('body').append(template);

	// Get the config file : viewer.ttl
	var viewerUri = window.location.origin + '/assets/ldp/viewer.ttl';
	var graph = graphsCache[viewerUri] =  new $rdf.IndexedFormula();
	var fetch = $rdf.fetcher(graph);
	fetch.nowOrWhenFetched(viewerUri, undefined, function () {
		//var data = new $rdf.Serializer(graph).toN3(graph);
		//console.log(data);

		// Check ressource type.
		var viewerJsUri, cpt = 0;
		_.each(rdfTypesGlobal,  function(type) {
			var vjs = graph.any(type, STAMPLE("view"));
			if (vjs && cpt < 1) {viewerJsUri = vjs.uri; cpt++;}
		});

		if (viewerJsUri != undefined) {
			// Load the menu.
			loadScript("/assets/ldp/js/menuViewer.js", function() {
				loadScript(viewerJsUri, null);
			});
		}
		else
			throw new Error('no viewer can render any of the RDF document types: ' + rdfTypesGlobal);

	});
}, 'html');
*/