PersonView = Backbone.View.extend({
	tagName:"li",
	initialize: function() {
		var self = this;
		console.log('initialize');
		// Render.
		this.render();

		// Bind event to model.
		this.model.on('change', function() {
			console.log('there is a change !!');
			self.render();
;		});
	},

	render: function() {
		console.log('render');

		// Define id.
		this.id = this.model.id;

		// Compile the template using underscore.
		var template = _.template( $("#person_template").html(), this.model);

		// Load the compiled HTML into the backbone el.
		this.$el.html( template);

		// Return.
		return this.el;
	}
});