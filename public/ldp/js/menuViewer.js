var templateURI = "/assets/ldp/templates/menuTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
	var LDP = $rdf.Namespace("http://www.w3.org/ns/ldp#");
	var RDF = $rdf.Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	var STAMPLE = $rdf.Namespace("http://ont.stample.co/2013/display#");

	// Load useful js.
	loadScript("/assets/ldp/js/createContainerFromString.js", null);
	loadScript("/assets/ldp/js/createFileFromString.js", null);

	// Load Html.
	var template = _.template(data, tab);

	// Append in the DOM.
	$('#viewerContent').append(template);


	// Get the config file : viewer.ttl
	var viewerUri = window.location.origin + '/assets/ldp/viewer.ttl';
	var graph2 = graphsCache[viewerUri] =  new $rdf.IndexedFormula();
	var fetch2 = $rdf.fetcher(graph2);
	fetch2.nowOrWhenFetched(viewerUri, undefined, function () {
		// Check ressource type.
		var viewerJsUri;
		_.each($rdf.types,  function(type) {
			var vjs = graph2.any(type, STAMPLE("view"));
			if (vjs) viewerJsUri = vjs.uri
		});

		// Load related viewer.
		loadScript(viewerJsUri, null);
	});

	// Bind events to Menu Dom elements.
	$(".newCategory").on("click", function(e) {
		showCloudNew('dir');
	});
	$(".newFile").on("click", function(e) {
		showCloudNew('file');
	});
	$("#create-item").on("keypress", function(e) {
		cloudListen(e)
	});
	$("#submit-item").on("click", function(e) {
		createItem();
	});
	$("#cancel-item").on("click", function(e) {
		hideCloud();
	});

	// Relative functions.
	function createItem() {
		var res = document.getElementById("create-item");
		if (res.name === 'file') {
			var success = function() {
				window.location.reload();
			};
			var error = function() {
				window.location.reload();
			};
			createFileFromString(res.value, $rdf.baseUri, success, error, null);
		}
		else if (res.name === 'dir') {
			var success = function() {
				window.location.reload();
			};
			var error = function() {
				window.location.reload();
			};
			createContainerFromString(res.value, $rdf.baseUri, success, error, null);
		}
	}

	function cloudListen(e) {
		// 13 = the Enter key
		if (e.which == 13 || e.keyCode == 13) {
			createItem();
		}
	}

	function showCloudNew(type) {
		var $createItem = $('#create-item');
		var text;
		if (type == 'file')
			text = 'file name...';
		else
			text = 'directory name...';
		hideImage();
		$createItem.attr('name', type);
		$createItem.attr('placeholder', text);
		$createItem.show();
		$createItem.focus();
		$('#submit-item').show();
		$('#cancel-item').show();
	}

	function hideCloud() {
		var $createItem = $('#create-item');
		$createItem.hide();
		$createItem.val('');
		$('#submit-item').hide();
		$('#cancel-item').hide();
	}

	function hideImage() {
		document.imageform.reset();
		$('#addimage').hide();
		$('#submit-image').hide();
		$('#cancel-image').hide();
	}

}, 'html');
