function main () {
	Backbone.Linked.bootstrap(
		function() {
			var events = {};
			_.extend(events,  Backbone.Linked.Events);

			// Load necessary scripts.
			loadScript("/assets/profiles/js/backbone/views/personView.js", null);
			loadScript("/assets/profiles/js/backbone/views/ownerView.js", null);

			// Register namespaces.
			var BL = Backbone.Linked;
			BL.registerNamespaces({
				STAMPLE: "http://ont.stample.co/2013/display#",
				WEBAPP: "http://ns.rww.io/wapp#"
			});

			// Use this proxy to deal with CO requests
			//BL.LDPResource.proxy = "http://data.fm/proxy?uri={uri}";
			BL.LDPResource.proxy = "http://localhost:9000/srv/cors?url={uri}";

			// Bootstrap with a WebId
			var defaultWebId = 'https://localhost:8443/2013/backbone#me';

			// Create person model.
			var Person = BL.Model.extend({
				label: function() {
					if(this.get('foaf:name') !== undefined) {
						return this.get('foaf:name');
					} else {
						return "Contact with URI: "+this.uri;
					}
				}
			});

			// Define the people collection (read only)
			People = BL.Collection.extend({
				model: Person,
				genera  tor: "{ <"+defaultWebId+"> foaf:knows ?id }"
			});

			// Build the domain objects
			//var peopleCollection = new People();
			window.peopleCollection = new People();
			var profile = new Person({'@id': defaultWebId});
			Backbone.Linked.setLogLevel('debug');
			profile.fetch({
				headers: {'accept': 'text/turtle'},
				success:function (response) {
					var ownerView = new OwnerView({model: profile});
					$('#ownerContainer').append(ownerView.$el);

					peopleCollection.each(function(contact) {
						var personView = new PersonView({model: contact});
						$('#container').append(personView.$el);
						contact.fetch({
							success: function() {
								contact.trigger('change', contact);
							}
						});
					});
				},
				error:function () {
					alert("Sorry, cannot retrieve the requested profile.");
				}
			});
		}
	);
}