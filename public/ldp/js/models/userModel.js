var UserModel = {

    initialize: function(uri) {
        console.log('Model Initialise ')
        console.log(this.defaults)
        this.uri = uri;
        this.attributes = this.defaults;
        return this;
    },

    // Fetch Model.
    fetch: function() {},

    setAttr: function(attr) {
        if (this.attributes.attr) this.attributes.attr = attr;
        return this;
    },

    getAttr: function(attr) {
        return (this.attributes.attr)? this.attributes.attr : null;
    },

    save: function(attr) {},

    destroy: function() {},

    defaults: {
        "name":"-",
        "imgUrl":"/assets/ldp/images/user_background.png",
        "nickname":"-",
        "email":"-",
        "phone":"-",
        "city":"-",
        "country":"-",
        "postalCode":"-",
        "street":"-",
        "birthday":"-",
        "gender":"-",
        "website":"-"
    }
}