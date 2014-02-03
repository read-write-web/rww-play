/**
 * Created by hjs on 23/01/2014.
 */

$rdf.Stmpl = {

    /**
     * remove hash from URL - this gets the document location
     * @param url
     * @returns {*}
     */
    fragmentless: function(url) {
        return url.split('#')[0];
    },

    isFragmentless: function(url) {
        return url.indexOf('#') == -1;
    },

    isFragmentlessSymbol: function(node) {
        return this.isSymbolNode(node) && this.isFragmentless(this.symbolNodeToUrl(node));
    },


    isLiteralNode: function(node) {
        return node.termType == 'literal';
    },
    isSymbolNode: function(node) {
        return node.termType == 'symbol';
    },
    isBlankNode: function(node) {
        return node.termType == 'bnode';
    },

    literalNodeToValue: function(node) {
        Preconditions.checkArgument(this.isLiteralNode(node), "Node is not a literal node:"+node);
        return node.value;
    },
    symbolNodeToUrl: function(node) {
        Preconditions.checkArgument(this.isSymbolNode(node), "Node is not a symbol node:"+node);
        return node.uri;
    }

    

}