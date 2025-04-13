var exec = require('cordova/exec');

var AudioOutputPicker = {
  getCurrentAudioOutput: function(success, error) {
    exec(success, error, 'AudioOutputPicker', 'getCurrentAudioOutput', []);
  },
  startAudioOutputListener: function(callback, error) {
    exec(callback, error, 'AudioOutputPicker', 'startAudioOutputListener', []);
  },
  stopAudioOutputListener: function(success, error) {
    exec(success, error, 'AudioOutputPicker', 'stopAudioOutputListener', []);
  }
};

module.exports = AudioOutputPicker;
