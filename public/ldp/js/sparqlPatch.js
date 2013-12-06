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