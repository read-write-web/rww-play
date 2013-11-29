function loadScript(sScriptSrc, callback) {
	var oHead = document.getElementsByTagName('head')[0];
	var oScript = document.createElement('script');
	oScript.type = 'text/javascript';
	oScript.src = sScriptSrc;

	// Then bind the event to the callback function.
	oScript.onreadystatechange = callback;   // IE 6 & 7
	oScript.onload = callback;

	oHead.appendChild(oScript);
}