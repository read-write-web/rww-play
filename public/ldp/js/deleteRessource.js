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