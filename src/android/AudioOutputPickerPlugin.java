package com.kuackmedia.plugins.audiooutputpicker;

import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

public class AudioOutputPickerPlugin extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("showAudioOutputPicker".equals(action)) {
            showAudioOutputPicker(callbackContext);
            return true;
        }
        callbackContext.error("Invalid action");
        return false;
    }

    private void showAudioOutputPicker(final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                AppCompatActivity activity = (AppCompatActivity) cordova.getActivity();

                MediaRouter mediaRouter = MediaRouter.getInstance(activity);
                MediaRouteSelector selector = new MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                        .build();

                MediaRouteChooserDialog chooserDialog = new MediaRouteChooserDialog(activity);
                chooserDialog.setRouteSelector(selector);
                chooserDialog.show();

                callbackContext.success("Audio Output Picker abierto");
            } catch (Exception e) {
                callbackContext.error("Error al abrir el diálogo: " + e.getMessage());
            }
        });
    }
}
