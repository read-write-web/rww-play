var ContactViewer = {
	attr: {
		profilePicture: "/assets/ldp/images/user_background.png",
		fullname:'No name',
		id:null
	},

	// Initialize.
	initialize : function(template, user) {
		var self = this;

		console.log('initialise');
		console.log(user);

		// Set the template.
		this.template = template;

		// Set user uri
		this.uri = user.uri;

		// Clean Uri.
		this.uriWoFragment = removeFragment(this.uri);

		// Render.
		this.render();

		// Fetch user information if not in cache.
		/*this.graph = graphsCache[this.uriWoFragment];
		if (!this.graph) {
			this.graph = graphsCache[this.uriWoFragment] = new $rdf.IndexedFormula();
			var fetch = $rdf.fetcher(this.graph);
			fetch.nowOrWhenFetched(this.uriWoFragment, undefined, function () {
				console.log('graph fetched !!!');
				self.render();
			});
		}
		else {
			self.render();
		}*/

		// Bind events to DOM elements.
		this.bindEventsToDom();
	},

	bindEventsToDom: function() {

	},

	bindEventsToView: function() {
		var self = this;

		//
		console.log($("#"+this.attr.id).find('.userContainer'));
		$("#"+this.attr.id).find('.userContainer').on("click", function() {
			console.log('Click : ' + self.attr.id);
			console.log(self.uri);
			window.location = self.uri;
		})
	},

	// Append to the DOM.
	render : function() {
		console.log('render');

		// Get useful attributes.
		/*/ Get name.
		var name = findFirst(this.graph, $rdf.sym(this.uri), FOAF('name'));
		this.attr.fullname = name.value;

		// Get images.
		var img = findFirst(this.graph, $rdf.sym(this.uri), FOAF('img'), FOAF('depiction'));
		this.attr.profilePicture = img.uri;
		*/

		//if (i == 1) renderUserBar(temp, user);
		//renderUserBar(temp, user);

		// Id
		this.attr.id = this.uriWoFragment.replace(/\/|\.|:|-|\~|_/g, "");

		// Name
		var name = findFirst(baseGraph, $rdf.sym(this.uri), FOAF('name'));
		this.attr.fullname = name.value;

		// Img
		var img = findFirst(baseGraph, $rdf.sym(this.uri), FOAF('img'), FOAF('depiction'));
		if (img) this.attr.profilePicture = img.value;

		// Define template.
		var obj = _.template(this.template, this.attr);

		// Append to DOM.
		$("#usersBar").append(obj);

		// Bind events to view elements.
		this.bindEventsToView();
	}
}
