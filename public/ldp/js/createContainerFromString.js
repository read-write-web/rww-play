function createContainerFromString(name, baseUri, callbackSuccess, callbackError, callbackDone) {
	var stringData = '@prefix ldp: <http://www.w3.org/ns/ldp#> .\n'+
		'@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n'+

		'<> a ldp:Container;\n'+
		'foaf:topic "A container for some type X of resources"; \n'+
		'foaf:maker <../card#me> . \n';

	$.ajax({
		type: "POST",
		url: baseUri,
		dataType: "text/turtle",
		contentType:"text/turtle",
		processData:false,
		data: stringData,
		headers: {"Slug": name},
		success: function(data, status, xhr) {
			console.log('sucesss');
			if (callbackSuccess) callbackSuccess()
		},
		error: function(xhr, status, error) {
			console.log('error');
			if (callbackError) callbackError()
		}
	})
		.done( function() {
			console.log('done ');
			if (callbackDone) callbackDone()
		});
}