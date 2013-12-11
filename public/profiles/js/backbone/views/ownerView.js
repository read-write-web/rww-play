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
		debugger

		var val = this.$("#inputName").val();
		//this.model.set('foaf:name', val);

		/*
		this.model.sync({
			success: function() {}
		});*/
		console.log('Saving.');
		console.log(this);
		this.model.save({'foaf:name': val}, {
			success: function() {
				console.log('success !');
			},
			error: function(){
				console.log('error !');
			}
		});

		this.$("#inputName").hide();
	},

	render:function () {
		this.$el.html(_.template($("#owner_template").html(), this.model));
		return this.el;
	}

});
