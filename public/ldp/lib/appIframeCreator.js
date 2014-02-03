

/**
 * A simple utility class which permits to create a fullscreen iframe
 * Created by sebastien on 1/31/14.
 */
function createAppIframe(iframeSrc,iframeData) {
    var iframe = document.createElement('iframe');
    iframe.style.width = "100%";
    iframe.style.height = "100%";
    iframe.style.position = "fixed";
    iframe.style.top = "0px";
    iframe.style.left = "0px";
    iframe.style.bottom = "0px";
    iframe.style.right = "0px";
    iframe.style.border = "none";
    iframe.style.margin = "0";
    iframe.style.padding = "0";
    iframe.style.overflow = "hidden";
    iframe.style.zIndex = "999999";
    iframe.src = iframeSrc;
    iframe.onload = function() {
        var targetOrigin = "*";
        iframe.contentWindow.postMessage(iframeData,targetOrigin);
    }
    window.document.body.innerHTML = ''
    window.document.body.margin = "0";
    window.document.body.appendChild(iframe);
}
