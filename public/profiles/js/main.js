function main () {
	Backbone.Linked.bootstrap(
		function() {
			// Bootstrap with a WebId
			var defaultWebId = 'https://my-profile.eu/people/deiu/card#me';
			//var defaultWebId = 'https://localhost:8443/card#me';
			console.log('main loaded !');

			// Register namespaces.
			var BL = Backbone.Linked;
			BL.registerNamespaces({
				STAMPLE: "http://ont.stample.co/2013/display#",
				WEBAPP: "http://ns.rww.io/wapp#",
			});

			// Create person model.
			//var Person =  BL.Model.extend();
			var Person =  BL.Collection.extend();
			var modelPerson = new Person({'uri': defaultWebId});
			modelPerson.fetch({
				success:function (response) {
					console.log("success");
					console.log(modelPerson.attributes);

					BL.RDFStore.execute('SELECT * where {?s ?i ?p}', function(s, r){
						console.log('RDF Store');
						console.log(r);
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