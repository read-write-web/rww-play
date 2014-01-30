var ContactViewer = {
	attr: {
		profilePicture: "/assets/ldp/images/user_background.png",
		fullname:'No name',
		id:null
	},

	// Initialize.
	initialize : function(pointedGraph, template) {
		var self = this;

		console.log('initialize');
		console.log(pointedGraph);

		// Set the template.
		this.template = template;

		// Set corresponding PG.
		this.pointedGraph = pointedGraph;

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
			console.log(App);
			App.loadUser(self.pointedGraph);

			//window.location = self.uri;
		})
	},

	getContactAttributes:function (callback) {
		var userPg = this.pointedGraph;
		console.log("getUserAttributes");
		console.log(userPg);

		// add name
		var namesPg = this.pointedGraph.rel(FOAF('name'));
		var names =
			_.chain(namesPg)
				.filter(function (pg) {
					return pg.pointer.termType == 'literal';
				})
				.map(function (pg) {
					return pg.pointer
				})
				.value();
		this.attr.fullname = (names && names.length > 0 ) ? names[0].value : "No value";
		console.log(this.attr.fullname);

		// Add image if available
		var imgsPg1 = this.pointedGraph.rel(FOAF('img'));
		var imgsPg2 = this.pointedGraph.rel(FOAF('depiction'));
		var imgs =
			_.chain(imgsPg1.concat(imgsPg2))
				.map(function (pg) {
					return pg.pointer
				})
				.value();
		this.attr.profilePicture = (imgs && imgs.length > 0 ) ? imgs[0].value : "No profile picture";
		console.log(this.attr.profilePicture);

		// Callback.
		if (callback) callback();
	},

	// Append to the DOM.
	render : function() {
		var self = this;
		console.log('render');

		// Get contact attributes.
		this.getContactAttributes(function() {
			// Set view Id.
			console.log(self.pointedGraph.graphName);
			self.attr.id = self.pointedGraph.graphName.uri.replace(/\/|\.|:|-|\~|_/g, "");

			// Define template.
			var html = _.template(self.template, self.attr);

			// Append to DOM.
			$("#usersBar").append(html);

			// Bind events to view elements.
			self.bindEventsToView();
		});
	}
}
