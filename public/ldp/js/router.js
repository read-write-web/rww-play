var router = {

	initialize: function() {
		// Start watching for hash change events
		Backbone.history.start();
	},

	routes:{
		"":"index",
		"dashboard": "goToDashboard",
		"c/:category":"goToCategory",
		"x/sharedwithme":"goToSharedWithMe",
		"treeview":"goToTreeView",
		"x/:creator/:category":"goToSharedCategory",
		"x/:creator": "goToCreatorVirtualCategory",
		"s/:stample":"goToStample",
		"print/:stample":"goToPrintStample",
		"u/:user":"goToUser",
		"?/:query":"goToResults",
		'*path': "goToHome"
	},

	// Reset flags and trigger garbage collection
	reset: function () {

	},

	// Route to home
	index: function () {

		this.reset();

		mixpanel.track("Home page");

		console.log("index");

		if (this.appView) { this.appView.renderGoToIndex(); }
		else { this.appView = new AppView({"flag": "goToIndex"}); }
	},

	// If requested route doesn't exist, go to home
	goToHome : function () {
		this.reset();

		console.log("goToHome");

		//window.location = "/";

		mixpanel.track("User requested non-existing route");
	}
}