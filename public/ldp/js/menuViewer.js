var templateURI = "https://localhost:8443/assets/ldp/templates/menuTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
	var LDP = $rdf.Namespace("http://www.w3.org/ns/ldp#");
	var RDF = $rdf.Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	var STAMPLE = $rdf.Namespace("http://ont.stample.co/2013/display#");

	// Load Html.
	var template = _.template(data, tab);

	// Append in the DOM.
	$('.cloudactions').append(template);

	// Get the config file : viewer.ttl
	var viewerUri = 'https://localhost:8443/assets/ldp/viewer.ttl';
	var graph2 = graphsCache[viewerUri] =  new $rdf.IndexedFormula();
	var fetch2 = $rdf.fetcher(graph2);
	fetch2.nowOrWhenFetched(viewerUri, undefined, function () {
		// Select related viewer.js.
		var viewerJs;
		if ($rdf.type === 1) {
			viewerJs = graph2.any( LDP('Container'), STAMPLE("view"));
		}
		else {
			viewerJs = graph2.any( LDP('Ressource'), STAMPLE("view"));
		}
		var viewerJsUri = viewerJs.uri;

		// Load related viewer (Container / Ressource).
		loadScript(viewerJsUri, function() {
			console.log('loaded !!!');
		});
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
		var onResult, onDone;
		//var baseUri = $rdf.baseUri;
		console.log(res.name+' / val='+res.value);
		if (res.name === 'file') {
			//cloud.append(res.value);
		}
		else if (res.name === 'dir') {
			//cloud.mkdir(res.value);



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
		if (type == 'file')
			var text = 'file name...';
		else
			var text = 'directory name...';
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
		$('addimage').hide();
		$('submit-image').hide();
		$('cancel-image').hide();
	}

}, 'html');
