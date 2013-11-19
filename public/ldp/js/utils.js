// Get basename.
var basename = function (path) {
	if (path.substring(path.length - 1) == '/')
		path = path.substring(0, path.length - 1);

	var a = path.split('/');
	return a[a.length - 1];
};


// Format from Unix time.
var formatTime = function(mtime) {
	var a = new Date(mtime*1);
	var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
	var year = a.getFullYear();
	var month = months[a.getMonth()];
	var date = a.getDate();
	var hour = a.getHours();
	var min = a.getMinutes();
	var sec = a.getSeconds();
	var time = year+'-'+month+'-'+date+' '+hour+':'+min+':'+sec + " GMT";
	return time;
};


// Check if uri point to a directory.
var isDirectory = function(uri) {
	var res;
	res = (uri.substring(uri.length - 1) == '/')? true: false;
	return res;
};
