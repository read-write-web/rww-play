PersonView = Backbone.View.extend({
	tagName:"div",
	template: '<div class="personContainer" id="<%= scopedVariable["@id"] %>"> +' +
		'<span><%= scopedVariable["foaf:homepage"] %></span></div>',
	initialize: function() {
		console.log('initialize');
		// Render.
		this.render();
	},

	render: function() {
		console.log('render');
		this.$el.html(_.template(this.template,
			{scopedVariable: this.model.toCompactJSON()}));
		console.log(this);
		return this.el;
	}
});