var templateURI = "/assets/ldp/templates/aclEditorTemplate.html";

// For quick access to those namespaces Todo: make this global!
var RDF = $rdf.Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
var WAC = $rdf.Namespace("http://www.w3.org/ns/auth/acl#");
var FOAF = $rdf.Namespace("http://xmlns.com/foaf/0.1/");

// Get ressource name.
var ressourceUri = $rdf.ressourceUri;
var ressourceName = basename(ressourceUri);
var tab = {"name":ressourceName};

$.get(templateURI, function(data) {
	var $viewContainer = $("#container");

	// Load template.
	var template = _.template(data,  tab);

	// Append to the DOM.
	$viewContainer.append(template);

	// Bind events to view elements.
	var $readInput = $('input[name=Read]');
	var $writeInput = $('input[name=Write]');
	var $appendInput = $('input[name=Append]');
	var $wacRecursive = $('#wac-recursive');
	$viewContainer.find($readInput).bind('click', function(e) {
		if ($writeInput.is(':checked')) {
			$writeInput.attr('checked', false);
		}
	});
	$viewContainer.find($writeInput).bind('click', function(e) {
		if (!$readInput.is(':checked')) {
			$readInput.attr('checked', true);
		}
	});
	$viewContainer.find($appendInput).bind('click', function(e) {});
	$viewContainer.find("#wac-recursive").bind("click", function(e){});
	$viewContainer.find('.cancel').bind('click', function(e) {
		$viewContainer.find('#wac-editor').remove();
	});
	$viewContainer.find('.save').bind('click', function(e) {
		saveAcl();
	});

	function saveAcl() {
		var read = $readInput.is(':checked');
		var write = $writeInput.is(':checked');
		var append = $appendInput.is(':checked');
		var recursive = $wacRecursive.is(':checked');
		var users = $('#wac-users')[0].value.split(",");

		var common = "<" + ressourceUri+".acl" + ">";
		var access = "<http://www.w3.org/ns/auth/acl#accessTo>";
		var agent = "<http://www.w3.org/ns/auth/acl#agent>";
		var agentClass = "<http://www.w3.org/ns/auth/acl#agentClass>";
		var mode = "<http://www.w3.org/ns/auth/acl#mode>";
		var request, accessTo, agents, modes;

		// Fetch the actual acl graph.
		var graph = new $rdf.IndexedFormula();
		var fetch = $rdf.fetcher(graph);
		fetch.nowOrWhenFetched(ressourceUri+".acl", undefined, function() {
			//var data = new $rdf.Serializer(graph).toN3(graph);
			//console.log(data);

			// Add accessTo.
			accessTo = "[] " + "acl:accessTo "  + "<" + ressourceUri + ">";

			// Add allowed users.
			if ((users.length > 0) && (users[0].length > 0)) {
				var i, n = users.length, user;
				agents = "acl:agent ";
				for (i=0;i<n;i++) {
					var user = users[i].replace(/\s+|\n|\r/g,'');
					if (i+1 == n) agents = agents + "<"+ user + ">";
					else agents = agents + "<"+ user + ">, ";
				}
			} else {
				agents = "	acl:agentClass foaf:Agent";
			}

			// Add access modes.
			modes = "	acl:mode " ;
			if (read == true) {
				modes = modes + "acl:Read, ";
			}
			if (write == true) {
				modes = modes + "acl:Write";
			}
			else if (append == true) {
				modes = modes + "acl:Append";
			}
			if (read == false && write == false) {
				$viewContainer.find('#wac-editor').remove();
				return;
			}

			// Make the SPARQL request.
			var sparqlQuery =
				"PREFIX acl: <http://www.w3.org/ns/auth/acl#> \n" +
				"PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n" +
				"INSERT DATA { \n" +
					accessTo + ";\n" +
					modes + ";\n" +
					agents + ".\n" +
					"}"

			// Send / PUT the new ACLs to the server.
			$.ajax({
				type: "PATCH",
				url: ressourceUri+'.acl',
				contentType: 'application/sparql-update',
				dataType: 'text',
				processData:false,
				data: sparqlQuery,
				success: function() {
					console.log('Saved !!!');
				},
				error: function() {
					console.log('Error !!!');
				}
			});
		});
	}
});