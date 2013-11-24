var templateURI = "/assets/ldp/templates/bodyTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
    // Load related CSS.
	loadCSS("/assets/ldp/css/blueprint.css");
    loadCSS("/assets/ldp/css/common.css");
    loadCSS("/assets/ldp/css/font-awesome.min.css");
    loadCSS("/assets/ldp/css/buttons.css");
	loadCSS("/assets/ldp/css/style.css");

    // Load Html.
    var template = _.template(data, tab);

	// Append in the DOM.
	$('body').append(template);

	// Load the menu.
	loadScript("/assets/ldp/js/menuViewer.js", null);
	// Load utils js.
	loadScript("/assets/ldp/js/utils.js", null);

}, 'html');
