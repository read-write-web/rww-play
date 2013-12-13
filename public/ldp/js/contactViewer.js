var ContactViewer = {
	attr: {
		profilePicture: "/assets/ldp/images/user_background.png",
		fullname:'No name',
		id:null
	},

	// Initialize.
	initialize : function(user, template) {
		var self = this;

		console.log('initialise');
		console.log(user);

		// Set the template.
		this.template = template;

		// Set corresponding PG.
		this.pg = user;

		// Set user uri
		this.uri = this.pg.pointer.uri;

		// Clean Uri.
		this.uriWoFragment = removeFragment(this.pg.pointer.uri);

		// Render.
		this.render();

		// Bind events to DOM elements.
		this.bindEventsToDom();
	},

	bindEventsToDom: function() {

	},

	bindEventsToView: function() {
		var self = this;
		$("#"+this.attr.id).find('.userContainer').on("click", function() {
			console.log('Click : ' + self.attr.id);
			console.log(self.uri);
			//window.location = self.uri;
		})
	},

	// Append to the DOM.
	render : function() {
		console.log('render');

		// Id
		this.attr.id = this.uriWoFragment.replace(/\/|\.|:|-|\~|_/g, "");

		// Name
		var name = findFirst(this.pg.graph, $rdf.sym(this.uri), FOAF('name'));
		this.attr.fullname = name.value;

		// Img
		var img = findFirst(this.pg.graph, $rdf.sym(this.uri), FOAF('img'), FOAF('depiction'));
		if (img) this.attr.profilePicture = img.value;

		// Define template.
		var obj = _.template(this.template, this.attr);

		// Append to DOM.
		$("#usersBar").append(obj);

		// Bind events to view elements.
		this.bindEventsToView();
	}
}
