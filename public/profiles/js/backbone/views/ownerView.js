OwnerView = Backbone.View.extend({
	tagName:"div",

	initialize:function () {
		var self = this;
		this.render();

		// Events bind to model changes.
		this.model.on("change:foaf:name", function() {
			self.render();
		});
	},

	events:{
		"click #ediLink": "editName",
		"submit form": "submitName"
	},

	editName: function(e) {
		this.$("#inputName")
			.show()
			.focus();
	},

	submitName: function(e) {
		var val = this.$("#inputName").val();
		this.model.set('foaf:name', val);
		debugger
		this.model.sync({
			success: function() {}
		});

		/*
		this.model.save({'foaf:name': val}, {
			success: function() {
				console.log('success !');
			},
			error: function(){

			}
		});*/
		this.$("#inputName").hide();
	},

	render:function () {
		this.$el.html(_.template($("#owner_template").html(), this.model));
		return this.el;
	}

});
