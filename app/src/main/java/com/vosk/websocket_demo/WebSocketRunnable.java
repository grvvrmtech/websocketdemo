package com.vosk.websocket_demo;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Log;
import android.widget.TextView;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

public class WebSocketRunnable implements Runnable {

    // Constants for indicating the state of the download
    static final int WEBSOCKET_STATE_FAILED = -1;
    static final int WEBSOCKET_STATE_STARTED = 0;
    static final int WEBSOCKET_TEXT = 1;
    public static final int RECORDER_STOPED = 2;
    public static final int RECORDER_STARTED = 3;
    // Sets a tag for this class
    private static final String LOG_TAG = "WebSocketRunnable";

    // Defines a field that contains the calling object of type WebSocketTask.
    final TaskRunnableWebSocketMethods mWebSocketTask;

    interface TaskRunnableWebSocketMethods{
        /**
         * Sets the Thread that this instance is running on
         * @param currentThread the current Thread
         */
        void setWebSocketThread(Thread currentThread);

        /**
         * Defines the actions for each state of the PhotoTask instance.
         * @param state The current state of the task
         */
        void handleWebSocketState(int state);

        /**
         * Gets the URL for the image being downloaded
         * @return The image URL
         */
        WebSocket getWebSocket();

        Context getContext();

        CountDownLatch getRecievedLatch();

        AudioRecord getAudioRecorder();

        boolean getFileFlag();

        void setMessage(String message);
        String getMessage();
        void setRecievedLatch(CountDownLatch recievedLatch);
    }

    WebSocketRunnable(TaskRunnableWebSocketMethods webSocketTask){mWebSocketTask = webSocketTask;}

    @Override
    public void run() {
        mWebSocketTask.setWebSocketThread(Thread.currentThread());
        // Moves the current Thread into the background
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        try {
            if (Thread.interrupted()) {

                throw new InterruptedException();
            }
            mWebSocketTask.getWebSocket().addListener(new WebSocketAdapter(){
                @Override
                public void onTextMessage(WebSocket websocket, String text) throws Exception {
                    super.onTextMessage(websocket, text);
                    mWebSocketTask.setMessage(text);
                    mWebSocketTask.handleWebSocketState(WEBSOCKET_TEXT);
                    mWebSocketTask.getRecievedLatch().countDown();
                }
            });
            mWebSocketTask.getWebSocket().connect();
            mWebSocketTask.handleWebSocketState(WEBSOCKET_STATE_STARTED);
            if (Thread.interrupted()) {
                    throw new InterruptedException();
            }

            if (mWebSocketTask.getFileFlag())
                runFileTranscript();
            else
                runLiveTranscript();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void runFileTranscript() throws IOException, InterruptedException {
        InputStream fis = mWebSocketTask.getContext().getAssets().open("test16k.wav");
        DataInputStream dis = new DataInputStream(fis);
        byte[] buf = new byte[16000];
        while (true) {
            int nbytes = dis.read(buf);
            if (nbytes < 0) break;

            mWebSocketTask.setRecievedLatch(new CountDownLatch(1));
            mWebSocketTask.getWebSocket().sendBinary(buf);
            mWebSocketTask.getRecievedLatch().await();
        }
    }

    private void runLiveTranscript() throws InterruptedException {

        AudioRecord recorder = mWebSocketTask.getAudioRecorder();
        recorder.startRecording();

        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
            recorder.stop();
            mWebSocketTask.handleWebSocketState(RECORDER_STOPED);
            return;
        }

        mWebSocketTask.handleWebSocketState(RECORDER_STARTED);
        int bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        byte[] buffer = new byte[bufferSize];

        while (true) {
            if (Thread.interrupted())
            {
                recorder.stop();
                mWebSocketTask.getWebSocket().disconnect();
                mWebSocketTask.handleWebSocketState(RECORDER_STOPED);
                break;
                //throw new InterruptedException();
            }
            int nread = recorder.read(buffer, 0, buffer.length);
            if (nread < 0) {
                throw new RuntimeException("error reading audio buffer");
            } else {
                mWebSocketTask.setRecievedLatch(new CountDownLatch(1));
                mWebSocketTask.getWebSocket().sendBinary(buffer);
                mWebSocketTask.getRecievedLatch().await();
            }
        }


    }
}
