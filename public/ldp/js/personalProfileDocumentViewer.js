console.log("PersonalProfileDocument view");
$('#viewerContent').empty()

var FOAF = $rdf.Namespace('http://xmlns.com/foaf/0.1/');
var RDF = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');

// Get base graph and uri.
var baseUri = $rdf.baseUri;
var baseGraph = $rdf.graphsCache[baseUri];
console.log("baseGraphString: " + baseGraph.toNT())



function getSubjectsOfType(g,subjectType) {
    var triples = g.statementsMatching(undefined,
        RDF('type'),
        subjectType,
        $rdf.sym(baseUri));
    return _.map(triples,function(triple) { return triple['subject']; })
}



var personUris = getSubjectsOfType(baseGraph,FOAF("Person"));
console.log("Persons found = " + personUris);

var personToDisplay = personUris[0]['value'];
console.log("Person to display:" + personToDisplay);


loadCSS("/assets/apps/people/a_css/general.css")
var socialBookTemplateURI = "/assets/apps/people/social_book_template.html";
$.get(socialBookTemplateURI, function(socialBookTemplate) {
    $('#viewerContent').append(socialBookTemplate)
}, 'html');


loadScript("/assets/apps/people/social_book.js",function() {
    //loadUser('http://bblfish.net/people/henry/card#me');
    loadUser(personToDisplay);
});



