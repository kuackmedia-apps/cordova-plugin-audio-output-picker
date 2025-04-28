package com.kuackmedia.plugins.audiooutputpicker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class AudioOutputPickerPlugin extends CordovaPlugin {
    private static final String TAG = "AudioOutputPicker";
    private CallbackContext eventCallbackContext = null;
    private AudioManager audioManager;
    private boolean isReceiverRegistered = false;

    private final BroadcastReceiver audioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Evento recibido: " + action);

            // Analicemos el evento específico para mejorar el manejo
            String eventType = "unknown";
            if (action != null) {
                if (action.contains("CONNECTED") || action.contains("DEVICE_ADDED") || action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    eventType = "connection";
                } else if (action.contains("DISCONNECTED") || action.contains("DEVICE_REMOVED") || action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    eventType = "disconnection";
                }
            }

            final String finalEventType = eventType;
            Log.d(TAG, "Tipo de evento detectado: " + finalEventType);

            // Esperar un poco para que el sistema actualice el estado de audio
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Pequeña pausa para asegurar que el sistema actualice el estado de los dispositivos
                        Thread.sleep(500);

                        if (eventCallbackContext != null) {
                            JSONObject deviceInfo = getAudioOutputInfo();
                            try {
                                deviceInfo.put("event", action);
                                deviceInfo.put("eventType", finalEventType);
                                // Comprobar si el evento es de conexión Bluetooth para BluetoothAdapter.ACTION_STATE_CHANGED
                                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                                    deviceInfo.put("bluetoothState", state);
                                    // Si Bluetooth acaba de encenderse pero no se detecta ningún dispositivo aún,
                                    // es posible que necesitemos darle más tiempo para conectarse
                                    if (state == BluetoothAdapter.STATE_ON && "speaker".equals(deviceInfo.getString("deviceType"))) {
                                        Thread.sleep(1000); // Espera adicional para dispositivos lentos
                                        deviceInfo = getAudioOutputInfo(); // Intenta obtener la info actualizada
                                        deviceInfo.put("event", action);
                                        deviceInfo.put("eventType", finalEventType);
                                        deviceInfo.put("retry", true);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error al agregar información al JSON", e);
                            }

                            PluginResult result = new PluginResult(PluginResult.Status.OK, deviceInfo);
                            result.setKeepCallback(true);
                            eventCallbackContext.sendPluginResult(result);
                            Log.d(TAG, "Evento enviado al callback: " + deviceInfo.toString());
                        } else {
                            Log.w(TAG, "Evento recibido pero no hay callback registrado");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar evento", e);
                    }
                }
            });
        }
    };

    @Override
    public void pluginInitialize() {
        Log.d(TAG, "Inicializando plugin AudioOutputPicker");

        audioManager = (AudioManager) cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

        // Registraremos el receptor más tarde cuando se llame a startAudioOutputListener
    }

    // Método adicional para verificar el estado de los dispositivos de audio
    // y emitir actualizaciones si es necesario
    private void startPeriodicCheck() {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000); // Esperar 2 segundos antes de comenzar

                    String previousDeviceType = null;

                    while (eventCallbackContext != null) {
                        JSONObject currentDevice = getAudioOutputInfo();
                        String currentDeviceType = currentDevice.getString("deviceType");

                        // Si cambió el tipo de dispositivo desde la última verificación, enviar un evento
                        if (previousDeviceType != null && !previousDeviceType.equals(currentDeviceType)) {
                            Log.d(TAG, "Cambio de dispositivo detectado en verificación periódica: " +
                                    previousDeviceType + " -> " + currentDeviceType);

                            try {
                                currentDevice.put("event", "periodic_check");
                                currentDevice.put("eventType", currentDeviceType.equals("bluetooth") ? "connection" : "disconnection");
                                currentDevice.put("previousDevice", previousDeviceType);
                            } catch (JSONException e) {
                                Log.e(TAG, "Error al agregar información de verificación periódica", e);
                            }

                            PluginResult result = new PluginResult(PluginResult.Status.OK, currentDevice);
                            result.setKeepCallback(true);
                            eventCallbackContext.sendPluginResult(result);
                            Log.d(TAG, "Evento de verificación periódica enviado: " + currentDevice.toString());
                        }

                        previousDeviceType = currentDeviceType;
                        Thread.sleep(5000); // Verificar cada 5 segundos
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error en verificación periódica", e);
                }
            }
        });
    }

    private void registerReceiver() {
        if (isReceiverRegistered) {
            Log.d(TAG, "Receptor ya está registrado, no es necesario registrarlo de nuevo");
            return;
        }

        // Crear un nuevo IntentFilter con todos los eventos que queremos escuchar
        IntentFilter filter = new IntentFilter();

        // Eventos para dispositivos de audio con cable
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);

        // Eventos para Bluetooth - usar solo acciones publicas documentadas
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        // Versión con prioridades para aumentar la posibilidad de recibir los eventos
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        //Log.d(TAG, "Registrando receptor para: " + Arrays.toString(filter.getActionsArray()));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cordova.getActivity().registerReceiver(audioReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                Log.d(TAG, "Receptor registrado con RECEIVER_NOT_EXPORTED");
            } else {
                cordova.getActivity().registerReceiver(audioReceiver, filter);
                Log.d(TAG, "Receptor registrado sin flags");
            }

            isReceiverRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Error al registrar receptor", e);
        }
    }

    private void unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                cordova.getActivity().unregisterReceiver(audioReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "Receptor de audio desregistrado");
            } catch (Exception e) {
                Log.e(TAG, "Error al desregistrar receptor", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver();
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "Ejecutando acción: " + action);

        switch (action) {
            case "getCurrentAudioOutput":
                JSONObject deviceInfo = getAudioOutputInfo();
                callbackContext.success(deviceInfo);
                Log.d(TAG, "getCurrentAudioOutput retornó: " + deviceInfo.toString());
                return true;

            case "startAudioOutputListener":
                this.eventCallbackContext = callbackContext;
                registerReceiver(); // Registramos el receptor solo cuando se inicia el listener

                // Iniciar verificación periódica
                startPeriodicCheck();

                // Enviar primer estado actual
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject currentDevice = getAudioOutputInfo();
                        try {
                            currentDevice.put("event", "initial_state");
                        } catch (JSONException e) {
                            Log.e(TAG, "Error al agregar evento inicial", e);
                        }

                        PluginResult initialResult = new PluginResult(PluginResult.Status.OK, currentDevice);
                        initialResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(initialResult);
                        Log.d(TAG, "Estado inicial enviado: " + currentDevice.toString());
                    }
                });

                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
                Log.d(TAG, "Listener de audio iniciado");
                return true;

            case "stopAudioOutputListener":
                this.eventCallbackContext = null;
                unregisterReceiver(); // Desregistramos el receptor cuando se detiene el listener
                callbackContext.success("Listener detenido");
                Log.d(TAG, "Listener de audio detenido");
                return true;

            default:
                callbackContext.error("Invalid action");
                Log.e(TAG, "Acción inválida: " + action);
                return false;
        }
    }

    private JSONObject getAudioOutputInfo() {
        JSONObject result = new JSONObject();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG, "Obteniendo dispositivos de audio en Android 6+");
                AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

                Log.d(TAG, "Número de dispositivos de salida: " + devices.length);

                // Primer paso: buscar dispositivos Bluetooth activos
                for (AudioDeviceInfo deviceInfo : devices) {
                    if (deviceInfo.isSink()) {
                        int type = deviceInfo.getType();
                        String typeStr = getDeviceTypeString(type);
                        String productName = deviceInfo.getProductName().toString();
                        Log.d(TAG, "Dispositivo encontrado: tipo=" + typeStr + ", nombre=" + productName);

                        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            result.put("deviceType", "bluetooth");
                            result.put("deviceName", productName);
                            return result;
                        }
                    }
                }

                // Segundo paso: buscar auriculares con cable
                for (AudioDeviceInfo deviceInfo : devices) {
                    if (deviceInfo.isSink()) {
                        int type = deviceInfo.getType();
                        if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                            result.put("deviceType", "wired_headset");
                            result.put("deviceName", "Auriculares cableados");
                            return result;
                        }
                    }
                }

                // Comprobación adicional para Bluetooth usando métodos alternativos
                if (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn()) {
                    Log.d(TAG, "Bluetooth detectado mediante isBluetoothA2dpOn/isBluetoothScoOn");
                    result.put("deviceType", "bluetooth");
                    result.put("deviceName", "Dispositivo Bluetooth");
                    return result;
                }
            } else {
                Log.d(TAG, "Usando métodos legacy para detectar audio en Android <6");
                // Para versiones más antiguas
                if (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn()) {
                    Log.d(TAG, "Bluetooth A2DP o SCO está activado");
                    result.put("deviceType", "bluetooth");
                    result.put("deviceName", "Dispositivo Bluetooth");
                    return result;
                } else if (audioManager.isWiredHeadsetOn()) {
                    Log.d(TAG, "Auricular con cable detectado");
                    result.put("deviceType", "wired_headset");
                    result.put("deviceName", "Auriculares cableados");
                    return result;
                }
            }

            // Default
            Log.d(TAG, "Ningún dispositivo especial detectado, usando altavoz");
            result.put("deviceType", "speaker");
            result.put("deviceName", "Altavoz del teléfono");
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener información del dispositivo de audio", e);
            try {
                result.put("deviceType", "error");
                result.put("deviceName", "Error: " + e.getMessage());
            } catch (JSONException je) {
                // Ignorar
            }
        }
        return result;
    }

    // Método auxiliar para convertir tipos de dispositivo a strings para logging
    private String getDeviceTypeString(int type) {
        switch(type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "BLUETOOTH_A2DP";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "BLUETOOTH_SCO";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "WIRED_HEADPHONES";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "WIRED_HEADSET";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "BUILTIN_SPEAKER";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "BUILTIN_EARPIECE";
            default: return "TIPO_" + type;
        }
    }
}
