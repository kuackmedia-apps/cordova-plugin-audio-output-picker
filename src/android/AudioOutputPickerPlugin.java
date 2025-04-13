package com.kuackmedia.plugins.audiooutputpicker;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AudioOutputPickerPlugin extends CordovaPlugin {

    private CallbackContext eventCallbackContext = null;
    private final BroadcastReceiver audioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (eventCallbackContext != null) {
                JSONObject deviceInfo = getAudioOutputInfo();
                PluginResult result = new PluginResult(PluginResult.Status.OK, deviceInfo);
                result.setKeepCallback(true);
                eventCallbackContext.sendPluginResult(result);
            }
        }
    };

    @Override
    public void pluginInitialize() {
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        cordova.getActivity().registerReceiver(audioReceiver, filter);
    }

    @Override
    public void onDestroy() {
        cordova.getActivity().unregisterReceiver(audioReceiver);
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "getCurrentAudioOutput":
                JSONObject deviceInfo = getAudioOutputInfo();
                callbackContext.success(deviceInfo);
                return true;
            case "startAudioOutputListener":
                this.eventCallbackContext = callbackContext;
                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
                return true;
            case "stopAudioOutputListener":
                this.eventCallbackContext = null;
                callbackContext.success("Listener detenido");
                return true;
            default:
                callbackContext.error("Invalid action");
                return false;
        }
    }

    private JSONObject getAudioOutputInfo() {
        AudioManager audioManager = (AudioManager) cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        JSONObject result = new JSONObject();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (AudioDeviceInfo deviceInfo : devices) {
                    if (deviceInfo.isSink()) {
                        int type = deviceInfo.getType();
                        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            result.put("type", "bluetooth");
                            result.put("name", deviceInfo.getProductName());
                            return result;
                        } else if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                            result.put("type", "wired_headset");
                            result.put("name", "Auriculares cableados");
                            return result;
                        }
                    }
                }
            }
            // Default
            result.put("type", "speaker");
            result.put("name", "Altavoz del teléfono");
        } catch (JSONException e) {
            // Manejo de excepción
        }
        return result;
    }
}
