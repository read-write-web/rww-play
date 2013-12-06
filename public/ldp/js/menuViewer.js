var templateURI = "/assets/ldp/templates/menuTemplate.html";
var tab = {};
$.get(templateURI, function(data) {
	// Load Html.
	var template = _.template(data, tab);

	// Append in the DOM.
	$('#viewerContent').append(template);

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
