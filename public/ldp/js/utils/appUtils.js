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