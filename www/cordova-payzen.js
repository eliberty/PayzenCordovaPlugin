/**
 * cordova CordovaPayzen plugin
 * Copyright (c) Eliberty Services SAS by Ludovic Menu
 *
 */
 (function(cordova){
    var CordovaPayzen = function() {

    };


    CordovaPayzen.prototype.startActivity = function (params, success, fail) {
        return cordova.exec(function (args) {
            success(args);
        }, function (args) {
            fail(args);
        }, 'CordovaPayzen', 'startActivity', [params]);
    };

    window.CordovaPayzen = new CordovaPayzen();

    // backwards compatibility
    window.plugins = window.plugins || {};
    window.plugins.CordovaPayzen = window.CordovaPayzen;

})(window.PhoneGap || window.Cordova || window.cordova);
