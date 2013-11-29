function main () {
	Backbone.Linked.bootstrap(
		function() {
			// Load necessary scripts.
			loadScript("/assets/profiles/js/backbone/views/personView.js", null);

			// Register namespaces.
			var BL = Backbone.Linked;
			BL.registerNamespaces({
				STAMPLE: "http://ont.stample.co/2013/display#",
				WEBAPP: "http://ns.rww.io/wapp#"
			});
			BL.LDPResource.proxy = "http://data.fm/proxy?uri={uri}";

			// Bootstrap with a WebId
			//var defaultWebId = 'https://my-profile.eu/people/deiu/card#me';
			//var defaultWebId = 'http://bblfish.net/people/henry/card#me';
			var defaultWebId = 'https://localhost:8443/2013/card#me';
			console.log('main loaded !');

			// Create person model.
			//var Person =  BL.Model.extend();
			People = BL.Collection.extend({
				generator: {
					subject: 'http://bblfish.net/people/henry/card#me',
					predicate: 'foaf:knows',
					object: 'ldp:MemberSubject'
				}
			});

			window.peopleCollection = new People({'uri': defaultWebId});
			window.person = new BL.Model({'@id': "https://my-profile.eu/people/deiu/card#me"});

			Backbone.Linked.setLogLevel('debug');
			peopleCollection.fetch({
				headers: {'accept': 'text/turtle'},
				success:function (response) {
					console.log("success");
					peopleCollection.each(function(model) {
						console.log(model);
						var personView = new PersonView({model:  model});
						model.fetch({
							success: function(r){
								debugger
								console.log('success');
								console.log(r);
							},
							error: function(r){
								console.log(r);
							}
						});
						$('#container').append(personView.$el);
					});

					debugger;
				},
				error:function () {
					console.log('error');
				}
			});
		}
	);
}