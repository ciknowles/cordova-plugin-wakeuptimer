var exec = require("cordova/exec");

/**
 * This is a global variable called wakeup exposed by cordova
 */    
var Wakeup = function(){};

Wakeup.prototype.wakeup = function(success, error, options) {
    exec(success, error, "WakeupPlugin", "wakeup", [options]);
};

Wakeup.prototype.snooze = function(success, error, options) {
    exec(success, error, "WakeupPlugin", "snooze", [options]);
};

Wakeup.prototype.cancelAlarm = function(success, error, ids) {
    ids = Array.isArray(ids) ? Array.from(ids) : [ids];
    exec(success, error, "WakeupPlugin", "cancelAlarm", ids);
};

Wakeup.prototype.playSound = function(success, error, sound) {
	sound = sound || "";
    exec(success, error, "WakeupPlugin", "playSound", [sound]);
};

Wakeup.prototype.stopSound = function(success, error) {
    exec(success, error, "WakeupPlugin", "stopSound", []);
};

Wakeup.prototype.getLaunchDetails = function(success, error) {
    exec(success, error, "WakeupPlugin", "getLaunchDetails", []);
};

module.exports = new Wakeup();