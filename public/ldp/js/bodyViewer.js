var templateURI = "https://localhost:8443/assets/ldp/templates/bodyTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
    // Load related CSS.
	loadCSS("css/blueprint.css");
    loadCSS("css/common.css");
    loadCSS("css/font-awesome.min.css");
    loadCSS("css/buttons.css");
	loadCSS("css/style.css");

    // Load Html.
    var template = _.template(data, tab);

	// Append in the DOM.
	$('body').append(template);

	// Load the menu.
	loadScript("https://localhost:8443/assets/ldp/js/menuViewer.js", null);
}, 'html');
