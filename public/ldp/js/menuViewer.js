var MenuView = {
	initialize: function(baseUri) {
		console.log('Initialise menu');
		var self = this;
		var templateURI = "/assets/ldp/templates/menuTemplate.html";
        this.baseUri = baseUri;

		$.get(templateURI, function(template) {
			// Set template.
			self.template = template;

			// render.
			self.render();
		});

		// Bind events to the DOM.
		this.bindEventsToDom();
	},

	bindEventsToDom: function() {},

	bindEventsToView: function() {
		var self = this;

		// Bind events to Menu Dom elements.
		$(".newCategory").on("click", function(e) {
			self.showCloudNew('dir');
		});
		$(".newFile").on("click", function(e) {
			self.showCloudNew('file');
		});
		$("#create-item").on("keypress", function(e) {
			self.cloudListen(e)
		});
		$("#submit-item").on("click", function(e) {
			self.createItem();
		});
		$("#cancel-item").on("click", function(e) {
			self.hideCloud();
		});

	},

	// Relative functions.
	createItem: function () {
		var res = document.getElementById("create-item");
		if (res.name === 'file') {
			var success = function () {
				window.location.reload();
			};
			var error = function () {
				window.location.reload();
			};
			createFileFromString(res.value, this.baseUri, success, error, null);
		}
		else if (res.name === 'dir') {
			var success = function () {
				window.location.reload();
			};
			var error = function () {
				window.location.reload();
			};
			createContainerFromString(res.value, this.baseUri, success, error, null);
		}
	},

	cloudListen: function (e) {
		// 13 = the Enter key
		if (e.which == 13 || e.keyCode == 13) {
			this.createItem();
		}
	},

	showCloudNew: function (type) {
		var $createItem = $('#create-item');
		var text;
		if (type == 'file')
			text = 'file name...';
		else
			text = 'directory name...';
		this.hideImage();
		$createItem.attr('name', type);
		$createItem.attr('placeholder', text);
		$createItem.show();
		$createItem.focus();
		$('#submit-item').show();
		$('#cancel-item').show();
	},

	hideCloud: function () {
		var $createItem = $('#create-item');
		$createItem.hide();
		$createItem.val('');
		$('#submit-item').hide();
		$('#cancel-item').hide();
	},

	hideImage: function () {
		document.imageform.reset();
		$('#addimage').hide();
		$('#submit-image').hide();
		$('#cancel-image').hide();
	},

	render: function () {
		console.log('render');
		// Load Html.
		var htmlAndData = _.template(this.template, {});

		// Append in the DOM.
		$('#menuContainer').append(htmlAndData);

		// Bind events to view elements.
		this.bindEventsToView();
	}
};


