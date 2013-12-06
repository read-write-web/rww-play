/*
* Helper to select the first existing element of a series of arguments
* */
function findFirst (graph, uriSym) {
	var obj = undefined,
		i = 0;
	while (!obj && i < arguments.length) {
		if (i>1) obj = graph.any(uriSym, arguments[i]);
		i += 1;
	}
	return obj
};

/*
 * Clean URI.
 * i.e. : look for hashbang in URL and remove it and anything after it
 * */
function removeFragment(uri) {
	var docURI, indexOf = uri.indexOf('#');
	if (indexOf >= 0)
		docURI = uri.slice(0, indexOf);
	else  docURI = uri;
	return docURI;
}


/*
* Sparql requests
* */
//PATCH
function sparqlPatch(uri, query, success, error, done) {
	$.ajax({
		type: "PATCH",
		url: uri,
		contentType: 'application/sparql-update',
		dataType: 'text',
		processData:false,
		data: query,
		success: function() {
			if (success) success()
		},
		error: function() {
			if (error) error()
		}
	}).done( function() {
			if (done) done()
		});
}

//GET
function sparqlGet(uri, query, success, error, done) {
	$.ajax({
		type: "GET",
		url: uri,
		contentType: 'application/sparql-update',
		dataType: 'text',
		processData:false,
		data: query,
		success: function() {
			if (success) success()
		},
		error: function() {
			if (error) error()
		}
	}).done( function() {
			if (done) done()
		});
}

/*
* Create a container
* */
function createContainerFromString(name, baseUri, callbackSuccess, callbackError, callbackDone) {
	var stringData = '@prefix ldp: <http://www.w3.org/ns/ldp#> .\n'+
		'@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n'+

		'<> a ldp:Container;\n'+
		'foaf:topic "A container for some type X of resources"; \n'+
		'foaf:maker <../card#me> . \n';

	$.ajax({
		type: "POST",
		url: baseUri,
		dataType: "text",
		contentType:"text/turtle",
		processData:false,
		data: stringData,
		headers: {"Slug": name},
		success: function(data, status, xhr) {
			if (callbackSuccess) callbackSuccess()
		},
		error: function(xhr, status, error) {
			if (callbackError) callbackError()
		}
	})
		.done( function() {
			if (callbackDone) callbackDone()
		});
}


/*
* Create a new file.
* */
function createFileFromString(name, baseUri, callbackSuccess, callbackError, callbackDone) {
	var stringData = '@prefix ldp: <http://www.w3.org/ns/ldp#> .\n'+
		'@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n'+

		'<> a ldp:Ressource;\n'+
		'foaf:topic "A File for some type X of resources"; \n'+
		'foaf:maker <../card#me> . \n';

	$.ajax({
		type: "POST",
		url: baseUri,
		dataType: "text",
		contentType:"text/turtle",
		processData:false,
		data: stringData,
		headers: {"Slug": name},
		success: function(data, status, xhr) {
			if (callbackSuccess) callbackSuccess()
		},
		error: function(xhr, status, error) {
			if (callbackError) callbackError()
		}
	})
		.done( function() {
			if (callbackDone) callbackDone()
		});
}


/*
* Delete a given ressource.
* */
function deleteRessource(uri, callbackSuccess, callbackError, callbackDone) {
	$.ajax({
		type: "DELETE",
		url:uri,
		accept:"text/turtle",
		success: function() {
			console.log('sucesss');
			if (callbackSuccess) callbackSuccess()
		},
		error: function() {
			console.log('error');
			if (callbackError) callbackError()
		}
	}).done( function() {
			console.log('done');
			if (callbackDone) callbackDone()
		});
}