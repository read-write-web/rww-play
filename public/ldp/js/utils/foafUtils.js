var foafUtils = {

    getFoafName: function (pg) {
        var tab = pg.getLiteral(FOAF('name'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafNick: function (pg) {
        var tab = pg.getLiteral(FOAF('nick'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafImg: function (pg) {
        var tab = pg.getSymbol(FOAF('img'), FOAF('depiction'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafMbox: function (pg) {
        var tab = pg.getSymbol(FOAF('mbox'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafPhone: function (pg) {
        var tab = pg.getSymbol(FOAF('phone'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafHomepage: function (pg) {
        var tab = pg.getSymbol(FOAF('homepage'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafGender: function (pg) {
        var tab = pg.getLiteral(FOAF('gender'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getFoafBirthday: function (pg) {
        var tab = pg.getLiteral(FOAF('birthday'));
        return (tab && tab.length>0)? tab[0] : null;
    },

    getPersonInfo: function (pg) {
        console.log("***********getFoafInfo*******************");
        var res = {};

        // Get a FOAF person info.
        res.img = this.getFoafImg(pg);
        res.name = this.getFoafName(pg);
        res.nick = this.getFoafNick(pg);
        res.mbox = this.getFoafMbox(pg);
        res.phone = this.getFoafPhone(pg);
        res.homepage = this.getFoafHomepage(pg);
        res.gender = this.getFoafGender(pg);
        res.birthday = this.getFoafBirthday(pg);

        // Return a javascript object.
        console.log(res);
        return res;
    },



//    getHomeInfo: function (relUri) {
//        var self = this;
//
//        //
//        var pgHomes = this.rel(CONTACT('home'));
//        console.log(pgHomes)
//
//        //
//        var tab = _.chain(pgHomes)
//            .map(function (pg) {
//                console.log(pg)
//                return pg.rel(relUri);
//            })
//            .flatten()
//            .value()
//
//        return tab;
//        /*
//         var tab = _.chain(arguments)
//         .map(function(arg) {
//         var pg = new $rdf.PointedGraph(self.graph, objectHome, self.graphName)
//         return pg.rel(arg);
//         })
//         .flatten()
//         .value();
//         console.log(tab);
//         var infoTab =
//         _.chain(tab)
//         .filter(function (pg) {
//         return (pg.pointer.termType == 'literal') || (pg.pointer.termType == 'symbol');
//         })
//         .map(function(pg) {
//         return pg.pointer.value
//         })
//         .value();
//
//         return (infoTab && _.size(infoTab)>0) ? infoTab[0] : null;
//         */
//    }
//
//    getLocationLat: function () {
//        return this.getLocInfo(GEOLOC('lat'));
//    }
//
//    getLocationLong: function () {
//        return this.getLocInfo(GEOLOC('long'));
//    }
//
//    getLocationAddress: function (pg) {
//        var r = this.getLocInfo(CONTACT('address'), pg);
//        console.log(r);
//        return null;
//    }
//
//    getLocationInfo: function () {
//        console.log("***********getLocationInfo*******************");
//        var res = {};
//
//        // Get Location information.
//        res.lat = this.getLocationLat();
//        res.long = this.getLocationLong();
//        var addrRes = this.getLocationAddress();
//        _.extend(res, addrRes);
//
//        // Return a javascript object.
//        return res;
//    }
//
//    getLocationInfoOld: function () {
//        console.log("***********getLocationInfo*******************");
//        var g = this.graph;
//        var n = this.graphName;
//        var p = this.pointer;
//        var res = {};
//
//        // Select all triples matching the foaf rel home.
//        var statementList1 = g.statementsMatching(p, CONTACT('home'), undefined, n, false);
//        if (statementList1 && statementList1.length > 0) {
//            var object = statementList1[0].object;
//            // Get latitude.
//            var lat = g.statementsMatching(object, GEOLOC('lat'), undefined, n, false);
//            if (lat[0].object.termType == 'literal') res.lat = lat[0].object.value;
//
//            // Get longitude.
//            var long = g.statementsMatching(object, GEOLOC('long'), undefined, n, false);
//            if (long[0].object.termType == 'literal') res.long = long[0].object.value;
//
//            // Get address.
//            var address = g.statementsMatching(object, CONTACT('address'), undefined, n, false);
//            var addrRes = this.getAddressInfo(address[0].object);
//            _.extend(res, addrRes);
//        }
//
//        // Return a javascript object.
//        return res;
//    }
//
//    getAddressInfoOld: function (subject) {
//        console.log("***********getAddressInfo*******************");
//        var g = this.graph;
//        var n = this.graphName;
//        var p = this.pointer;
//        var res = {};
//
//        // Select all triples with subject foaf 'address'.
//        var statementList = g.statementsMatching(subject, undefined, undefined, n, false);
//        _.map(statementList, function (stat) {
//            var uri = stat.predicate.uri;
//            var uriSplitTab = uri.split('#');
//            if (_.size(uriSplitTab) == 2) {
//                var key = uriSplitTab[1].toString()
//                res[key] = stat.object.value;
//            }
//        })
//
//        // Return a javascript object.
//        return res;
//    }


}