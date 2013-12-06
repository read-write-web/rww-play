console.log("PersonalProfileDocument view");
$('#viewerContent').empty()

var FOAF = $rdf.Namespace('http://xmlns.com/foaf/0.1/');
var RDF = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');

// Get base graph and uri.
var baseUri = $rdf.baseUri;
var baseGraph = $rdf.graphsCache[baseUri];



function getSubjectsOfType(g,subjectType) {
    var triples = g.statementsMatching(undefined,
        RDF('type'),
        subjectType,
        $rdf.sym(baseUri));
    return _.map(triples,function(triple) { return triple['subject']; })
}

function getFoafPrimaryTopic(g) {
    return g.any($rdf.sym(baseUri),FOAF('primaryTopic'),undefined)
}

function getPersonToDisplay(g) {
    var foafPrimaryTopic = getFoafPrimaryTopic(g)
    if ( foafPrimaryTopic ) {
        return foafPrimaryTopic;
    } else {
        var personUris = getSubjectsOfType(baseGraph,FOAF("Person"));
		console.log(personUris);
        if ( personUris.length === 0 ) {
            throw "No person to display in this card: " + baseUri;
        } else {
            return personUris[0];
        }
    }
}



var personToDisplay = getPersonToDisplay(baseGraph).value;
console.log("Person to display:" + personToDisplay);


loadCSS("/assets/apps/people/a_css/general.css")
var socialBookTemplateURI = "/assets/apps/people/social_book_template.html";
$.get(socialBookTemplateURI, function(socialBookTemplate) {
    $('#viewerContent').append(socialBookTemplate)
}, 'html');


loadScript("/assets/apps/people/social_book.js",function() {
    loadUser(personToDisplay);
});



