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
import androidx.core.content.ContextCompat;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioOutputPickerPlugin extends CordovaPlugin {
    private static final String TAG = "AudioOutputPicker";
    private boolean isLoggingEnabled = false; // New flag to control logging
    private CallbackContext eventCallbackContext = null;
    private AudioManager audioManager;
    private boolean isReceiverRegistered = false;
    private AtomicBoolean periodicCheckRunning = new AtomicBoolean(false);

    // New method to enable or disable logging
    public void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
    }

    private void log(String message) {
        if (isLoggingEnabled) {
            Log.d(TAG, message);
        }
    }

    private void logError(String message, Throwable throwable) {
        if (isLoggingEnabled) {
            Log.e(TAG, message, throwable);
        }
    }

    private final BroadcastReceiver audioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("Evento recibido: " + action);

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
            log("Tipo de evento detectado: " + finalEventType);

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
                                logError("Error al agregar información al JSON", e);
                            }

                            if (eventCallbackContext != null) { // Verificar de nuevo por si cambió mientras esperábamos
                                PluginResult result = new PluginResult(PluginResult.Status.OK, deviceInfo);
                                result.setKeepCallback(true);
                                eventCallbackContext.sendPluginResult(result);
                                log("Evento enviado al callback: " + deviceInfo.toString());
                            } else {
                                log("El callback fue anulado durante el procesamiento");
                            }
                        } else {
                            log("Evento recibido pero no hay callback registrado");
                        }
                    } catch (Exception e) {
                        logError("Error al procesar evento", e);
                    }
                }
            });
        }
    };

    @Override
    public void pluginInitialize() {
        log("Inicializando plugin AudioOutputPicker");

        audioManager = (AudioManager) cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

        // Registraremos el receptor más tarde cuando se llame a startAudioOutputListener
    }

    // Método adicional para verificar el estado de los dispositivos de audio
    // y emitir actualizaciones si es necesario
    private void startPeriodicCheck() {
        if (periodicCheckRunning.compareAndSet(false, true)) {
            log("Iniciando verificación periódica");

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000); // Esperar 2 segundos antes de comenzar

                        String previousDeviceType = null;

                        while (eventCallbackContext != null && periodicCheckRunning.get()) {
                            JSONObject currentDevice = getAudioOutputInfo();
                            String currentDeviceType = currentDevice.getString("deviceType");

                            // Si cambió el tipo de dispositivo desde la última verificación, enviar un evento
                            if (previousDeviceType != null && !previousDeviceType.equals(currentDeviceType)) {
                                log("Cambio de dispositivo detectado en verificación periódica: " +
                                        previousDeviceType + " -> " + currentDeviceType);

                                try {
                                    currentDevice.put("event", "periodic_check");
                                    currentDevice.put("eventType", currentDeviceType.equals("bluetooth") ? "connection" : "disconnection");
                                    currentDevice.put("previousDevice", previousDeviceType);
                                } catch (JSONException e) {
                                    logError("Error al agregar información de verificación periódica", e);
                                }

                                if (eventCallbackContext != null) { // Verificar de nuevo antes de enviar
                                    PluginResult result = new PluginResult(PluginResult.Status.OK, currentDevice);
                                    result.setKeepCallback(true);
                                    eventCallbackContext.sendPluginResult(result);
                                    log("Evento de verificación periódica enviado: " + currentDevice.toString());
                                }
                            }

                            previousDeviceType = currentDeviceType;
                            Thread.sleep(5000); // Verificar cada 5 segundos
                        }

                        log("Verificación periódica detenida");
                    } catch (Exception e) {
                        logError("Error en verificación periódica", e);
                    } finally {
                        periodicCheckRunning.set(false);
                    }
                }
            });
        }
    }

    private void stopPeriodicCheck() {
        periodicCheckRunning.set(false);
        log("Señal de detención para verificación periódica enviada");
    }

    private void registerReceiver() {
        if (isReceiverRegistered) {
            log("Receptor ya está registrado, no es necesario registrarlo de nuevo");
            return;
        }

        // Crear un nuevo IntentFilter con todos los eventos que queremos escuchar
        IntentFilter filter = new IntentFilter();

        // Eventos para dispositivos de audio con cable
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);

        // Bluetooth - todos los eventos posibles
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        // Añadir eventos específicos de A2DP y SCO
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED");

        // También monitorizar cambios en el AudioManager
        filter.addAction("android.media.ACTION_SCO_AUDIO_STATE_UPDATED");

        // Importante: monitorizar cambios generales en el routing de audio
        filter.addAction("android.media.AUDIO_DEVICE_ADDED");
        filter.addAction("android.media.AUDIO_DEVICE_REMOVED");

        // Versión con prioridades para aumentar la posibilidad de recibir los eventos
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        //log("Registrando receptor para: " + Arrays.toString(filter.getActionsArray()));

        try {
            // Usar ContextCompat para compatibilidad con diferentes versiones de Android
            ContextCompat.registerReceiver(
                    cordova.getActivity(),   // contexto
                    audioReceiver,           // receptor
                    filter,                  // filtro
                    ContextCompat.RECEIVER_NOT_EXPORTED  // flag
            );
            log("Receptor registrado con RECEIVER_NOT_EXPORTED usando ContextCompat");

            isReceiverRegistered = true;
        } catch (Exception e) {
            logError("Error al registrar receptor", e);
        }
    }

    private void unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                cordova.getActivity().unregisterReceiver(audioReceiver);
                isReceiverRegistered = false;
                log("Receptor de audio desregistrado");
            } catch (Exception e) {
                logError("Error al desregistrar receptor", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopPeriodicCheck();
        unregisterReceiver();
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        log("Ejecutando acción: " + action);

        switch (action) {
            case "getCurrentAudioOutput":
                JSONObject deviceInfo = getAudioOutputInfo();
                callbackContext.success(deviceInfo);
                log("getCurrentAudioOutput retornó: " + deviceInfo.toString());
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
                            logError("Error al agregar evento inicial", e);
                        }

                        if (eventCallbackContext != null) {
                            PluginResult initialResult = new PluginResult(PluginResult.Status.OK, currentDevice);
                            initialResult.setKeepCallback(true);
                            eventCallbackContext.sendPluginResult(initialResult);
                            log("Estado inicial enviado: " + currentDevice.toString());
                        }
                    }
                });

                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
                log("Listener de audio iniciado");
                return true;

            case "stopAudioOutputListener":
                log("Deteniendo listener de audio...");
                // Primero detener la verificación periódica
                stopPeriodicCheck();
                // Luego eliminar la referencia al callback
                this.eventCallbackContext = null;
                // Finalmente desregistrar el receptor
                unregisterReceiver();
                callbackContext.success("Listener detenido completamente");
                log("Listener de audio detenido completamente");
                return true;

            default:
                callbackContext.error("Invalid action");
                log("Acción inválida: " + action);
                return false;
        }
    }

    private JSONObject getAudioOutputInfo() {
        JSONObject result = new JSONObject();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                log("Obteniendo dispositivos de audio en Android 6+");
                AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

                log("Número de dispositivos de salida: " + devices.length);

                // Primer paso: buscar dispositivos Bluetooth activos
                for (AudioDeviceInfo deviceInfo : devices) {
                    if (deviceInfo.isSink()) {
                        int type = deviceInfo.getType();
                        String typeStr = getDeviceTypeString(type);
                        String productName = deviceInfo.getProductName().toString();
                        log("Dispositivo encontrado: tipo=" + typeStr + ", nombre=" + productName);

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
                    log("Bluetooth detectado mediante isBluetoothA2dpOn/isBluetoothScoOn");
                    result.put("deviceType", "bluetooth");
                    result.put("deviceName", "Dispositivo Bluetooth");
                    return result;
                }
            } else {
                log("Usando métodos legacy para detectar audio en Android <6");
                // Para versiones más antiguas
                if (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn()) {
                    log("Bluetooth A2DP o SCO está activado");
                    result.put("deviceType", "bluetooth");
                    result.put("deviceName", "Dispositivo Bluetooth");
                    return result;
                } else if (audioManager.isWiredHeadsetOn()) {
                    log("Auricular con cable detectado");
                    result.put("deviceType", "wired_headset");
                    result.put("deviceName", "Auriculares cableados");
                    return result;
                }
            }

            // Default
            log("Ningún dispositivo especial detectado, usando altavoz");
            result.put("deviceType", "speaker");
            result.put("deviceName", "Altavoz del teléfono");
        } catch (Exception e) {
            logError("Error al obtener información del dispositivo de audio", e);
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
