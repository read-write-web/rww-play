function loadCSS(cssPath, callback) {
    var head  = document.getElementsByTagName('head')[0];
    var link  = document.createElement('link');
    link.rel  = 'stylesheet';
    link.type = 'text/css';
    link.href = cssPath;
    link.media = 'screen, projection';

    // Then bind the event to the callback function.
    link.onreadystatechange = callback;
    link.onload = callback;

    // Append link to the head.
    head.appendChild(link);
}
