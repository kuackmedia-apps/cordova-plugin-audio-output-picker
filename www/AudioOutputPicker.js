var exec = require('cordova/exec');

var AudioOutputPicker = {
  showAudioOutputPicker: function(success, error) {
    exec(success, error, 'AudioOutputPicker', 'showAudioOutputPicker', []);
  }
};

module.exports = AudioOutputPicker;
