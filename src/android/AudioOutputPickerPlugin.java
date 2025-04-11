package com.kuackmedia.plugins.audiooutputpicker;

import android.content.Context;
import android.media.MediaRouter;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

public class AudioOutputPickerPlugin extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("showAudioOutputPicker".equals(action)) {
            this.showAudioOutputPicker(callbackContext);
            return true;
        }
        callbackContext.error("Invalid action");
        return false;
    }

    private void showAudioOutputPicker(final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MediaRouter mediaRouter = (MediaRouter) cordova.getActivity()
                        .getSystemService(Context.MEDIA_ROUTER_SERVICE);
                mediaRouter.openRouteChooserDialog(
                        MediaRouter.ROUTE_TYPE_LIVE_AUDIO, cordova.getActivity());
                callbackContext.success("Audio Output Picker abierto");
            }
        });
    }
}
