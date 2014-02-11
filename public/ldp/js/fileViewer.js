var AppStarter = {
	initialize: function(pointedGraph){
		var self = this;
		var templateURI = "/assets/ldp/templates/fileTemplate.html";

		// Load appropriate lib and scripts.
		this.loadVariousFiles();

        // Get current user relative URI.
        this.pointedGraph = pointedGraph;
        this.baseGraph = pointedGraph.store;
        this.baseUri = pointedGraph.namedGraphUrl.uri;
        this.baseGraphString = this.baseGraph.toString();

		// Get template and render();
		$.get(templateURI, function(template) {
			loadScript("/assets/ldp/js/menuViewer.js", function() {
				// Create menu.
				MenuView.initialize()

				self.template = template

				// Render.
				self.render();
			})
		}, 'html');
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

	// Render.
	render: function(){
		// Load and fill related templates.
		var tab = (this.baseGraphString) ?
		{"fileContent": this.baseGraphString}:
		{"fileContent":"Empty File !"};

		// Load template with data.
		var template = _.template(this.template, tab);

		// Append template in DOM.
		$('body').append(template);
	}
};

/*
console.log("File view");
var templateURI = "/assets/ldp/templates/fileTemplate.html";
var tab = {"fileContent":"Empty File !"};
$.get(templateURI, function(data) {
	// Get base graph and uri.
	var baseUri = baseUriGlobal;
	var baseGraph = graphsCache[baseUri];
	var baseGraphString = baseGraph.toString();

	// Load and fill related templates.
	var tab = (baseGraphString) ?
		{"fileContent": baseGraphString}:
		{"fileContent":"Empty File !"};

	// Load template with data.
	var template = _.template(data, tab);

	// Append template in DOM.
	$('#viewerContent').append(template);

}, 'html');
*/