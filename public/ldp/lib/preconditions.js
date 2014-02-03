/**
 * Created by sebastien on 1/28/14.
 */
var Preconditions = {
    checkArgument: function(condition, message) {
        if (!condition) {
            throw Error('IllegalArgumentException: ' + (message || ''));
        }
    }
}

