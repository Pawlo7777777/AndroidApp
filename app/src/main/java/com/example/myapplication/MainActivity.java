package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.FileDescriptor;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceListAdapter;
    private ArrayList<String> deviceList;
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    // Flashlight
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashlightOn = false;

    // Audio Recorder
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    // Text to Speech
    private TextToSpeech textToSpeech;

    // Speech to Text
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    // UI Components
    private Button btnBluetoothToggle, btnScanDevices, btnSendData, btnReceiveData, btnDisconnectBluetooth;
    private ListView listViewDevices;
    private ToggleButton toggleFlashlight;
    private Button btnRecordAudio, btnStopRecording;
    private EditText editTextTTS, editTextBluetoothSend;
    private Button btnSpeak;
    private Button btnStartSTT;
    private TextView textViewSTTResult, textViewBluetoothReceived, textViewRecordingStatus;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permissions
        requestAllPermissions();

        // Initialize UI
        initializeUI();

        // Initialize Features
        initializeBluetooth();
        initializeFlashlight();
        initializeTextToSpeech();
        initializeSpeechToText();
    }

    private void initializeUI() {
        // Bluetooth UI
        btnBluetoothToggle = findViewById(R.id.btnBluetoothToggle);
        btnScanDevices = findViewById(R.id.btnScanDevices);
        btnSendData = findViewById(R.id.btnSendData);
        btnReceiveData = findViewById(R.id.btnReceiveData);
        btnDisconnectBluetooth = findViewById(R.id.btnDisconnectBluetooth);
        listViewDevices = findViewById(R.id.listViewDevices);
        editTextBluetoothSend = findViewById(R.id.editTextBluetoothSend);
        textViewBluetoothReceived = findViewById(R.id.textViewBluetoothReceived);

        // Flashlight UI
        toggleFlashlight = findViewById(R.id.toggleFlashlight);

        // Audio Recorder UI
        btnRecordAudio = findViewById(R.id.btnRecordAudio);
        btnStopRecording = findViewById(R.id.btnStopRecording);
        textViewRecordingStatus = findViewById(R.id.textViewRecordingStatus);

        // TTS UI
        editTextTTS = findViewById(R.id.editTextTTS);
        btnSpeak = findViewById(R.id.btnSpeak);

        // STT UI
        btnStartSTT = findViewById(R.id.btnStartSTT);
        textViewSTTResult = findViewById(R.id.textViewSTTResult);

        // Setup listeners
        setupListeners();
    }

    private void setupListeners() {
        // Bluetooth Listeners
        btnBluetoothToggle.setOnClickListener(v -> toggleBluetooth());
        btnScanDevices.setOnClickListener(v -> scanDevices());
        btnSendData.setOnClickListener(v -> sendBluetoothData());
        btnReceiveData.setOnClickListener(v -> receiveBluetoothData());

        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            connectToDevice(position);
        });

        // Flashlight Listener
        toggleFlashlight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                turnOnFlashlight();
            } else {
                turnOffFlashlight();
            }
        });

        // Audio Recorder Listeners
        btnRecordAudio.setOnClickListener(v -> startRecording());
        btnStopRecording.setOnClickListener(v -> stopRecording());

        // TTS Listener
        btnSpeak.setOnClickListener(v -> speakText());

        // STT Listener
        btnStartSTT.setOnClickListener(v -> startSpeechToText());
    }

    // ==================== BLUETOOTH ====================

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceList = new ArrayList<>();
        bluetoothDevices = new ArrayList<>();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listViewDevices.setAdapter(deviceListAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    private void toggleBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothAdapter.disable();
            Toast.makeText(this, "Bluetooth turned off", Toast.LENGTH_SHORT).show();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void scanDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth scan permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        deviceList.clear();
        bluetoothDevices.clear();

        // Get paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
                bluetoothDevices.add(device);
            }
        }

        // Start discovery
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
        deviceListAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                String deviceInfo = device.getName() + "\n" + device.getAddress();
                if (!deviceList.contains(deviceInfo)) {
                    deviceList.add(deviceInfo);
                    bluetoothDevices.add(device);
                    deviceListAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    private void connectToDevice(int position) {
        BluetoothDevice device = bluetoothDevices.get(position);

        // Stop discovery before connecting (important!)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        }

        // Connect in a background thread
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Permission required", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Try secure connection first
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                } catch (IOException e) {
                    // If secure connection fails, try insecure connection
                    try {
                        bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                        bluetoothSocket.connect();
                    } catch (IOException e2) {
                        // If both fail, try reflection method (fallback)
                        try {
                            bluetoothSocket = (BluetoothSocket) device.getClass()
                                    .getMethod("createRfcommSocket", new Class[]{int.class})
                                    .invoke(device, 1);
                            bluetoothSocket.connect();
                        } catch (Exception e3) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed: " + e3.getMessage(), Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                }

                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show());

            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                try {
                    if (bluetoothSocket != null) {
                        bluetoothSocket.close();
                    }
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            }
        }).start();
    }

    private void sendBluetoothData() {
        if (outputStream == null) {
            Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = editTextBluetoothSend.getText().toString();
        try {
            outputStream.write(message.getBytes());
            Toast.makeText(this, "Data sent", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to send data", Toast.LENGTH_SHORT).show();
        }
    }

    private void receiveBluetoothData() {
        if (inputStream == null) {
            Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            try {
                bytes = inputStream.read(buffer);
                final String receivedData = new String(buffer, 0, bytes);
                runOnUiThread(() -> textViewBluetoothReceived.setText("Received: " + receivedData));
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to receive data", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==================== FLASHLIGHT ====================

    private void initializeFlashlight() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void turnOnFlashlight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, true);
                isFlashlightOn = true;
            }
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Failed to turn on flashlight", Toast.LENGTH_SHORT).show();
        }
    }

    private void turnOffFlashlight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, false);
                isFlashlightOn = false;
            }
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Failed to turn off flashlight", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== AUDIO RECORDER ====================

    // Replace the startRecording() and stopRecording() methods in MainActivity.java

    private void startRecording() {
        if (isRecording) {
            Toast.makeText(this, "Already recording", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show();
            requestAllPermissions();
            return;
        }

        // For Android 9 and below, check WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
                requestAllPermissions();
                return;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "AUDIO_" + timeStamp + ".3gp";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp");
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SoundRecorder");
                values.put(MediaStore.Audio.Media.IS_PENDING, 1);

                Uri audioUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

                if (audioUri == null) {
                    Toast.makeText(this, "Failed to create audio file", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Open file descriptor for the URI
                FileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(audioUri, "w").getFileDescriptor();

                // Store URI for later use in stopRecording
                audioFilePath = audioUri.toString();

                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setOutputFile(fileDescriptor);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            } else {
                // Android 9 and below - Use traditional file path
                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                File soundRecorderDir = new File(musicDir, "SoundRecorder");

                if (!soundRecorderDir.exists()) {
                    boolean created = soundRecorderDir.mkdirs();
                    if (!created) {
                        Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                audioFilePath = new File(soundRecorderDir, fileName).getAbsolutePath();

                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setOutputFile(audioFilePath);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            }

            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            textViewRecordingStatus.setText("Recording... " + fileName);
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (!isRecording) {
            Toast.makeText(this, "Not recording", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Mark the file as finished (not pending anymore)
                Uri audioUri = Uri.parse(audioFilePath);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                getContentResolver().update(audioUri, values, null, null);

                textViewRecordingStatus.setText("Saved to Music/SoundRecorder");
                Toast.makeText(this, "Recording saved to Music/SoundRecorder", Toast.LENGTH_LONG).show();
            } else {
                // Notify media scanner for Android 9 and below
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                File file = new File(audioFilePath);
                mediaScanIntent.setData(android.net.Uri.fromFile(file));
                sendBroadcast(mediaScanIntent);

                textViewRecordingStatus.setText("Saved: " + audioFilePath);
                Toast.makeText(this, "Recording saved to Music/SoundRecorder", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    // ==================== TEXT TO SPEECH ====================

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(MainActivity.this, "Language not supported", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void speakText() {
        String text = editTextTTS.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter text to speak", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // ==================== SPEECH TO TEXT ====================

    private void initializeSpeechToText() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                textViewSTTResult.setText("Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                textViewSTTResult.setText("Error occurred. Try again.");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    textViewSTTResult.setText("You said: " + matches.get(0));
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startSpeechToText() {
        speechRecognizer.startListening(speechRecognizerIntent);
    }

    // ==================== PERMISSIONS ====================

    private void requestAllPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup Bluetooth
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            unregisterReceiver(receiver);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Cleanup TTS
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        // Cleanup STT
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        // Cleanup Flashlight
        if (isFlashlightOn) {
            turnOffFlashlight();
        }
    }
}