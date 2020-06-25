package com.vosk.websocket_demo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebSocketManager {


    /*
     *   Status Indicator
     *
     */

    public static final int  WEBSOCKET_CONNECT_FAIL = 1;
    public static final int  WEBSOCKET_CONNECT_SUCCESS = 2;
    public static final int WEBSOCKET_TEXT = 3;
    public static final int RECORDER_STOPED = 4;
    public static final int RECORDER_STARTED = 5;





    // Sets the amount of time an idle thread will wait for a task before terminating
    private static final int KEEP_ALIVE_TIME = 1;

    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT ;

    // Sets the initial threadpool size to 8
    private static final int CORE_POOL_SIZE = 8;

    // Sets the maximum threadpool size to 8
    private static final int MAXIMUM_POOL_SIZE = 8;


    // A queue of Runnables for the image download pool
    private final BlockingQueue<Runnable> mWebSocketWorkQueue;

    // A queue of PhotoManager tasks. Tasks are handed to a ThreadPool.
    private final Queue<WebSocketTask> mWebSocketTaskWorkQueue;

    // A managed pool of background download threads
    private final ThreadPoolExecutor mWebSocketThreadPool;

    // An object that manages Messages in a Thread
    private Handler mHandler;

    private final WebSocketFactory mWebSocketFactory;

    // A single instance of PhotoManager, used to implement the singleton pattern
    private static WebSocketManager sInstance = null;

    final int sampleRate = 16000;
    int bufferSize;

    // A static block that sets class fields
    static {

        // The time unit for "keep alive" is in seconds
        KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

        // Creates a single static instance of PhotoManager
        sInstance = new WebSocketManager();

    }

    private WebSocketManager() {
        mWebSocketWorkQueue = new LinkedBlockingQueue<Runnable>();
        mWebSocketTaskWorkQueue = new LinkedBlockingQueue<WebSocketTask>();
        /*
         * Creates a new pool of Thread objects for the download work queue
         */
        mWebSocketThreadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mWebSocketWorkQueue);

        mWebSocketFactory = new WebSocketFactory();
        /*
         * Instantiates a new anonymous Handler object and defines its
         * handleMessage() method. The Handler *must* run on the UI thread, because it moves photo
         * Bitmaps from the PhotoTask object to the View object.
         * To force the Handler to run on the UI thread, it's defined as part of the PhotoManager
         * constructor. The constructor is invoked when the class is first referenced, and that
         * happens when the View invokes startDownload. Since the View runs on the UI Thread, so
         * does the constructor and the Handler.
         */
        mHandler = new Handler(Looper.getMainLooper()) {

            /*
             * handleMessage() defines the operations to perform when the
             * Handler receives a new Message to process.
             */
            @Override
            public void handleMessage(Message inputMessage) {

                // Gets the image task from the incoming Message object.
                WebSocketTask webSocketTask = (WebSocketTask) inputMessage.obj;

                // Sets an PhotoView that's a weak reference to the
                // input ImageView
                TextView localView = webSocketTask.getTextView();


                // If this input view isn't null
                if (localView != null) {
                    /*
                     * Chooses the action to take, based on the incoming message
                     */
                    switch (inputMessage.what) {

                        // If the download has started, sets background color to dark green
                        case WEBSOCKET_CONNECT_SUCCESS:
                            Log.i("messageHandle","WEBSOCKET_CONNECT_SUCCESS");
                            break;
                        case WEBSOCKET_CONNECT_FAIL:
                            Log.i("messageHandle","WEBSOCKET_CONNECT_FAIL");
                            // Attempts to re-use the Task object
                            recycleTask(webSocketTask);
                            break;
                        case RECORDER_STOPED:
                            Log.i("messageHandle","RECORDER_STOPED");
                            break;
                        case RECORDER_STARTED:
                            Log.i("messageHandle","RECORDER_STARTED");
                            break;
                        case WEBSOCKET_TEXT:
                            Log.i("messageHandle","WEBSOCKET_TEXT");
                            String message = webSocketTask.getMessage();
                            String temp = "";
                            JSONObject reader = null;
                            boolean isFinal = false;
                            try {
                                reader = new JSONObject(message);
                                if (reader.has("text")) {
                                    temp = reader.getString("text");
                                    isFinal = true;
                                    Log.i("full text ", temp);
                                } else if (reader.has("partial")) {
                                    temp = reader.getString("partial");
                                    Log.i("partial ", temp);
                                }

                                if (temp.length() > 0)
                                {
                                        Log.i("TRANSCRIPT", temp + '\n');
                                        if (isFinal)
                                        {
                                            localView.append(temp+'\n');
                                        }
                                }
                            }catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                            break;
                        default:
                            // Otherwise, calls the super method
                            super.handleMessage(inputMessage);
                    }
                }
            }
        };
    }

    /**
     * Returns the PhotoManager object
     * @return The global PhotoManager object
     */
    public static WebSocketManager getInstance() {

        return sInstance;
    }

    /**
     * Handles state messages for a particular task object
     * @param webSocketTask A task object
     * @param state The state of the task
     */
    @SuppressLint("HandlerLeak")
    public void handleState(WebSocketTask webSocketTask, int state) {
        switch (state) {
            // In all other cases, pass along the message without any other action.
            default:
                mHandler.obtainMessage(state, webSocketTask).sendToTarget();
                break;
        }

    }

    /**
     * Cancels all Threads in the ThreadPool
     */
    public static void cancelAll() {

        /*
         * Creates an array of tasks that's the same size as the task work queue
         */
        WebSocketTask[] taskArray = new WebSocketTask[sInstance.mWebSocketWorkQueue.size()];

        // Populates the array with the task objects in the queue
        sInstance.mWebSocketWorkQueue.toArray(taskArray);

        // Stores the array length in order to iterate over the array
        int taskArraylen = taskArray.length;

        /*
         * Locks on the singleton to ensure that other processes aren't mutating Threads, then
         * iterates over the array of tasks and interrupts the task's current Thread.
         */
        synchronized (sInstance) {

            // Iterates over the array of tasks
            for (int taskArrayIndex = 0; taskArrayIndex < taskArraylen; taskArrayIndex++) {

                // Gets the task's current thread
                Thread thread = taskArray[taskArrayIndex].mThreadThis;

                // if the Thread exists, post an interrupt to it
                if (null != thread) {
                    thread.interrupt();
                }
            }
        }
    }

    /**
     * Stops a download Thread and removes it from the threadpool
     *
     * @param webSocketTask The download task associated with the Thread
     */
    static public void removeWebSocket(WebSocketTask webSocketTask) {

        // If the Thread object still exists and the download matches the specified URL
        if (webSocketTask != null) {

            /*
             * Locks on this class to ensure that other processes aren't mutating Threads.
             */
            synchronized (sInstance) {

                // Gets the Thread that the downloader task is running on
                Thread thread = webSocketTask.getCurrentThread();

                // If the Thread exists, posts an interrupt to it
                if (null != thread)
                    thread.interrupt();
            }
            /*
             * Removes the download Runnable from the ThreadPool. This opens a Thread in the
             * ThreadPool's work queue, allowing a task in the queue to start.
             */
            sInstance.mWebSocketThreadPool.remove(webSocketTask.getWebSocketRunnable());
        }
    }

    private AudioRecord getAudioRecording() throws IOException {
        //bufferSize = 16000;//Math.round(sampleRate * BUFFER_SIZE_SECONDS);//
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
            recorder.release();
            throw new IOException(
                    "Failed to initialize recorder. Microphone might be already in use.");
        }
        return recorder;
    }

    /**
     * Starts an image download and decode
     *
     * @param textView The ImageView that will get the resulting Bitmap
     * @param cacheFlag Determines if caching should be used
     * @return The task instance that will handle the work
     */
    static public WebSocketTask startTranscript(
            TextView textView,
            String uri,
            Context context,
            boolean cacheFlag,
            boolean fileFlag) {

        /*
         * Gets a task from the pool of tasks, returning null if the pool is empty
         */
        WebSocketTask webSocketTask = sInstance.mWebSocketTaskWorkQueue.poll();
        if (null == webSocketTask) {
            webSocketTask = new WebSocketTask();
        }
        WebSocket webSocket = null;
        AudioRecord audioRecord = null;
        try {
            webSocket = sInstance.mWebSocketFactory.createSocket(uri);
            audioRecord = sInstance.getAudioRecording();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // If the queue was empty, create a new task instead.


        // Initializes the task
        webSocketTask.initializeWebSocketTask(WebSocketManager.sInstance, textView, webSocket, audioRecord, context, cacheFlag, fileFlag);

        sInstance.mWebSocketThreadPool.execute(webSocketTask.getWebSocketRunnable());

        textView.setText("STARTED WEBSOCKET");

        return webSocketTask;
    }

    /**
     * Recycles tasks by calling their internal recycle() method and then putting them back into
     * the task queue.
     * @param downloadTask The task to recycle
     */
    void recycleTask(WebSocketTask downloadTask) {

        // Frees up memory in the task
        downloadTask.recycle();

        // Puts the task object back into the queue for re-use.
        mWebSocketTaskWorkQueue.offer(downloadTask);
    }


}
