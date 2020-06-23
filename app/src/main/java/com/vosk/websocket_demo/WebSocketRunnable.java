package com.vosk.websocket_demo;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;

import java.io.DataInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

public class WebSocketRunnable implements Runnable {
    // Sets a tag for this class
    @SuppressWarnings("unused")
    private static final String LOG_TAG = "WebSocketRunnable";

    // Constants for indicating the state of the download
    static final int WEBSOCKET_STATE_FAILED = -1;
    static final int WEBSOCKET_STATE_STARTED = 0;
    static final int WEBSOCKET_TEXT = 1;


    private CountDownLatch recieveLatch;

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
                    mWebSocketTask.handleWebSocketState(WEBSOCKET_TEXT);
                    Log.i("TRANSCRIPT", text);
                    recieveLatch.countDown();
                }
            });
            mWebSocketTask.getWebSocket().connect();
            mWebSocketTask.handleWebSocketState(WEBSOCKET_STATE_STARTED);
            if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                InputStream fis = mWebSocketTask.getContext().getAssets().open("test16k.wav");
                DataInputStream dis = new DataInputStream(fis);
                byte[] buf = new byte[16000];
                while (true) {
                    int nbytes = dis.read(buf);
                    if (nbytes < 0) break;
                    recieveLatch = new CountDownLatch(1);
                    mWebSocketTask.getWebSocket().sendBinary(buf);
                    recieveLatch.wait();
                }


        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
