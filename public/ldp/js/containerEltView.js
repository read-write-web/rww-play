var containerEltView = {
	attr: {
		name: "_",
		size:"_",
		type:"_",
		mtime:"_"
	},

	initialize : function(data, template) {
		var self = this;

		// Set the template.
		this.template = template;

		// Set the corresponding container.
		this.container = data;

		// Render.
		this.render();

		// Bind events to DOM elements.
		this.bindEventsToDom();
	},

	bindEventsToDom: function() {

	},

	bindEventsToView: function() {
		var $lines = $('.lines');

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
	},

	render: function() {
		// Save ressource informations.
		this.attr.uri = this.container['?m'].uri;

		try {
			this.attr.name = basename(this.container['?m'].uri.toString())
		} catch (error) {
			this.attr.name = "!!**JS ERROR**!!"
		}

		// Get the type.
		try {
			this.attr.type = (this.container['?type'].value == ldp("Container") ) ? "Container" : "-"
		} catch (error) {
			this.attr.type = "-";
		}

		// Get the size.
		try {
			this.attr.size = this.container['?size'].value;
		} catch (error) {
			this.attr.size = "-";
		}

		// Get the modification time.
		try {
			this.attr.mtime = formatTime(this.container['?mt'].value);
		} catch (error) {
			this.attr.mtime = "-";
		}

		// Load and fill related templates.
		var htmlAndData = _.template(this.template, this.attr);

		// Append to the DOM.
		var $lines = $('.lines');
		$lines.append(htmlAndData);
	}
};