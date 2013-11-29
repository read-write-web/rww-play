var templateURI = "/assets/ldp/templates/ld-calTemplate.html";
var tab = {};

// ----- Modify if needed ------
var PROXY = "https://rww.io/proxy?uri={uri}";
//var AUTH_PROVIDER = "https://ld-cal.rww.io/";
var AUTH_PROVIDER = "https://localhost:8443/2013/";

// ----- DO NOT MODIFY BELOW -------
// App name
var appName = 'https://ld-cal.rww.io';
// Storage endpoint URI
var storageURI;
// User's WebID
var mywebid;
var mygraph;

var eventsHeader =
	'@prefix stample: <http://ont.stample.co/2013/display#> .' +
	'@prefix wapp: <http://ns.rww.io/wapp#> .' +
	'<> a stample:EventsDocument .' +
	'<#ld-cal>' +
	'wapp:description "Simple Linked Data calendar with agenda." ;' +
	'wapp:endpoint <https://apps.localhost:8443/Agenda_2> ;' +
	'wapp:name "LD-Cal" ;' +
	'wapp:serviceId <https://ld-cal.rww.io> ;' +
	'	a wapp:app .';

$.get(templateURI, function(data) {
	// Load related CSS.
	loadCSS("/assets/apps/ld-cal/css/fullcalendar.css");
	loadCSS("/assets/apps/ld-cal/css/fullcalendar.print.css");
	loadCSS("/assets/apps/ld-cal/css/buttons.css");
	loadCSS("/assets/apps/ld-cal/css/style.css");
	loadCSS("/assets/apps/ld-cal/css/classic.css");
	loadCSS("/assets/apps/ld-cal/css/classic.date.css");
	loadScript("/assets/apps/ld-cal/contrib/jquery-ui.custom.min.js", null);
	loadScript("/assets/apps/ld-cal/contrib/sha1.js", null);
	loadScript("/assets/apps/ld-cal/contrib/picker.js", function(){
		loadScript("/assets/apps/ld-cal/contrib/picker.date.js", null);
	});

	loadScript("/assets/apps/ld-cal/contrib/fullcalendar.min.js",
		function() {
			loadScript("/assets/apps/ld-cal/js/ld-cal.js",
			function() {
				// Empty container.
				$('#viewerContent').empty();

				// Load Html.
				var template = _.template(data, tab);

				// Append in the DOM.
				$('#viewerContent').append(template);

				// Render Calendar
				var user = "https://localhost:8443/2013/card#me";
				mywebid = user;
				once_authenticated(user);

			});
		});

}, 'html');
