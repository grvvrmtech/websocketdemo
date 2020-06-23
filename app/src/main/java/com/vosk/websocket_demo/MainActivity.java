package com.vosk.websocket_demo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import com.vosk.websocket_demo.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import tech.gusavila92.websocketclient.WebSocketClient;

import com.vosk.websocket_demo.*;


public class MainActivity extends AppCompatActivity {
    private WebSocketClient webSocketClient;
    private CountDownLatch recieveLatch;
    private int RECORD_AUDIO_REQUEST_CODE =123 ;



    private int sampleRate = 16000;
    private final static float BUFFER_SIZE_SECONDS = 0.4f;
    private int bufferSize;
    private AudioRecord recorder;
    private boolean stopRecording = false;

    private WebSocketFactory factory = new WebSocketFactory();
    private WebSocket webSocket = null;


    private final Handler mainHandler = new Handler(Looper.getMainLooper());



    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button transcribe_file = findViewById(R.id.transcribe_file);
        final Button transcribe_live = findViewById(R.id.transcribe_live);
        final Button stop_transcribe_live = findViewById(R.id.stop_transcribe_live);

        final TextView textView = findViewById(R.id.transcript);
        textView.setMovementMethod(new ScrollingMovementMethod());

        transcribe_file.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                    WebSocketManager.startTranscript(textView, "ws://192.168.105.95:2700", MainActivity.this, false);

                    /*new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {

                                //transcriptWavFile("test16k.wav");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, "Transcript Wave File").start();*/

                // Code here executes on main thread after user presses button
            }
        });
        transcribe_live.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    stopRecording = false;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                transcriptLive();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, "Transcribe Live").start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Code here executes on main thread after user presses button
            }
        });

        stop_transcribe_live.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopRecording = true;
            }
        });

        Log.i("WebSocket", "calling websocket function ");

        getPermissionToRecordAudio();
        try {
            AudioRecording();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private class WebsocketRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    /*private class WebSocketTask extends AsyncTask<Void, Void, String> {
        WebSocketFactory factory = null;
        WebSocket webSocket = null;

        public WebSocketTask(WebSocketFactory factory, WebSocket webSocket) throws IOException {
            this.factory = factory;
            this.webSocket = webSocket;

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            try {
                webSocket.connect();
            } catch (WebSocketException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            InputStream fis = getAssets().open("test16k.wav");
            DataInputStream dis = new DataInputStream(fis);
            byte[] buf = new byte[16000];
            while (true) {
                int nbytes = dis.read(buf);
                if (nbytes < 0) break;
                recieveLatch = new CountDownLatch(1);
                webSocket.sendBinary(buf);
                recieveLatch.wait();
            }
        }

    }*/



    private void AudioRecording() throws IOException {
        //bufferSize = 16000;//Math.round(sampleRate * BUFFER_SIZE_SECONDS);//
        bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
            recorder.release();
            throw new IOException(
                    "Failed to initialize recorder. Microphone might be already in use.");
        }
    }

    private void transcriptWavFile(String path) throws Exception {
        //FileInputStream fis = new FileInputStream(new File(path));



        InputStream fis = getAssets().open(path);
        DataInputStream dis = new DataInputStream(fis);
        byte[] buf = new byte[16000];
        while (true) {
            int nbytes = dis.read(buf);
            if (nbytes < 0) break;
            recieveLatch = new CountDownLatch(1);
            webSocketClient.send(buf);
            recieveLatch.await();
        }
        recieveLatch = new CountDownLatch(1);
        webSocketClient.send("{\"eof\" : 1}");
        recieveLatch.await();
        webSocketClient.close();
    }

    private boolean interrupted() {
        boolean temp = stopRecording;
        return temp;
    }

    private void transcriptLive() throws Exception {

        if(webSocketClient != null)
            webSocketClient.close();
        createWebSocketClient();
        recorder.startRecording();
        TextView textView = findViewById(R.id.transcript);
        textView.setText("");
        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
            recorder.stop();
            IOException ioe = new IOException(
                    "Failed to start recording. Microphone might be already in use.");
            //mainHandler.post(new OnErrorEvent(ioe));
            return;
        }

        byte[] buffer = new byte[bufferSize];

        while (!interrupted()) {
            int nread = recorder.read(buffer, 0, buffer.length);
            if (nread < 0) {
                throw new RuntimeException("error reading audio buffer");
            } else {
                recieveLatch = new CountDownLatch(1);
                webSocketClient.send(buffer);
                recieveLatch.await();
            }
        }
        recorder.stop();
        webSocketClient.close();
        //recorder.release();
    }




    private void createWebSocketClient() {
        URI uri;
        try {
            // Connect to local host
            uri = new URI("ws://192.168.105.95:2700");
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen() {
                Log.i("WebSocket", "Session is starting");
                //webSocketClient.send("Hello World!");
            }
            @Override
            public void onTextReceived(String s) {
                Log.i("WebSocket", "Message received");
                final String message = s;
                Log.i("Transcript",s);
                String temp = "";
                String partial_temp = null;
                JSONObject reader = null;

                try {

                    reader = new JSONObject(message);
                    if(reader.has("text")) {
                        temp = reader.getString("text");
                        Log.i("full text ", temp);
                    }
                    else if (reader.has("partial")) {
                        partial_temp = reader.getString("partial");
                        Log.i("partial ", partial_temp);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                temp = partial_temp;
                final String result_text = temp;

                //recieveLatch.countDown();
                if (result_text.length() > 0 ){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            TextView textView = findViewById(R.id.transcript);
                            textView.setText(result_text+'\n');
                            //recieveLatch.countDown();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                });
                }
                recieveLatch.countDown();

            }
            @Override
            public void onBinaryReceived(byte[] data) {
            }
            @Override
            public void onPingReceived(byte[] data) {
            }
            @Override
            public void onPongReceived(byte[] data) {
            }
            @Override
            public void onException(Exception e) {
                System.out.println(e.getMessage());
            }
            @Override
            public void onCloseReceived() {
                Log.i("WebSocket", "Closed ");
                System.out.println("onCloseReceived");
            }
        };
        //webSocketClient.setConnectTimeout(10000);
        //webSocketClient.setReadTimeout(60000);
        //webSocketClient.enableAutomaticReconnection(5000);
        webSocketClient.connect();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void getPermissionToRecordAudio() {
        // 1) Use the support library version ContextCompat.checkSelfPermission(...) to avoid
        // checking the build version since Context.checkSelfPermission(...) is only available
        // in Marshmallow
        // 2) Always check for permission (even if permission has already been granted)
        // since the user can revoke permissions at any time through Settings
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {

            // The permission is NOT already granted.
            // Check if the user has been asked about this permission already and denied
            // it. If so, we want to give more explanation about why the permission is needed.
            // Fire off an async request to actually get the permission
            // This will show the standard permission request dialog UI
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    RECORD_AUDIO_REQUEST_CODE);

        }
    }

    // Callback with the request from calling requestPermissions(...)
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        // Make sure it's our original READ_CONTACTS request
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.length == 3 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED){

                //Toast.makeText(this, "Record Audio permission granted", Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(this, "You must give permissions to use this app. App is exiting.", Toast.LENGTH_SHORT).show();
                finishAffinity();
            }
        }

    }



}