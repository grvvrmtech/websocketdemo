package com.vosk.websocket_demo;

import android.content.Context;
import android.media.AudioRecord;
import android.widget.TextView;
import com.neovisionaries.ws.client.WebSocket;
import com.vosk.websocket_demo.WebSocketRunnable.TaskRunnableWebSocketMethods;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.concurrent.CountDownLatch;


public class WebSocketTask implements TaskRunnableWebSocketMethods {
    private WeakReference<TextView> mTextViewWeakRef;
    // The WebSocket's URL
    private URL mWebSocketURL;
    /*
     * Field containing the Thread this task is running on.
     */
    Thread mThreadThis;

    private Runnable mWebSocketRunnable;

    // The Thread on which this task is currently running.
    private Thread mCurrentThread;

    // Is the cache enabled for this transaction?
    private boolean mCacheEnabled;

    /*
     * An object that contains the ThreadPool singleton.
     */
    private static WebSocketManager sWebSocketManager;

    private static WebSocket sWebSocket;

    private static AudioRecord sAudioRecord;

    private static Context sContext;

    CountDownLatch mRecievedLatch = null;

    private boolean mFileFlag;
    private String mMessage;

    WebSocketTask() {
        mWebSocketRunnable = new WebSocketRunnable(this);
    }

    /**
     * Initializes the Task
     *
     * @param webSocketManager A ThreadPool object
     * @param textView An TextView instance that shows the Transcript text
     * @param cacheFlag Whether caching is enabled
     */
    void initializeWebSocketTask(
            WebSocketManager webSocketManager,
            TextView textView,
            WebSocket webSocket,
            AudioRecord audioRecord,
            Context context,
            boolean cacheFlag,
            boolean fileFlag)
    {
        sWebSocketManager = webSocketManager;
        sWebSocket = webSocket;
        sAudioRecord = audioRecord;
        mTextViewWeakRef = new WeakReference<TextView>(textView);
        mCacheEnabled = cacheFlag;
        sContext = context;
        mFileFlag = fileFlag;
    }

    // Detects the state of caching
    boolean isCacheEnabled() {
        return mCacheEnabled;
    }

    // Delegates handling the current state of the task to the PhotoManager object
    void handleState(int state) {
        sWebSocketManager.handleState(this, state);
    }

    // Returns the instance that downloaded the image
    Runnable getWebSocketRunnable() {
        return mWebSocketRunnable;
    }

    /**
     * Recycles an PhotoTask object before it's put back into the pool. One reason to do
     * this is to avoid memory leaks.
     */
    void recycle() {

        // Deletes the weak reference to the imageView
        if ( null != mTextViewWeakRef ) {
            mTextViewWeakRef.clear();
            mTextViewWeakRef = null;
        }
    }

    // Returns the ImageView that's being constructed.
    public TextView getTextView() {
        if ( null != mTextViewWeakRef ) {
            return mTextViewWeakRef.get();
        }
        return null;
    }

    /*
     * Returns the Thread that this Task is running on. The method must first get a lock on a
     * static field, in this case the ThreadPool singleton. The lock is needed because the
     * Thread object reference is stored in the Thread object itself, and that object can be
     * changed by processes outside of this app.
     */
    public Thread getCurrentThread() {
        synchronized(sWebSocketManager) {
            return mCurrentThread;
        }
    }

    /*
     * Sets the identifier for the current Thread. This must be a synchronized operation; see the
     * notes for getCurrentThread()
     */
    public void setCurrentThread(Thread thread) {
        synchronized(sWebSocketManager) {
            mCurrentThread = thread;
        }
    }

    // Implements PhotoDownloadRunnable.setHTTPDownloadThread(). Calls setCurrentThread().
    @Override
    public void setWebSocketThread(Thread currentThread) {
        setCurrentThread(currentThread);
    }

    @Override
    public WebSocket getWebSocket() {
        return sWebSocket;
    }

    @Override
    public AudioRecord getAudioRecorder() {
        return sAudioRecord;
    }

    @Override
    public boolean getFileFlag() {
        return mFileFlag;
    }

    @Override
    public Context getContext() {
        return sContext;
    }


    @Override
    public void setRecievedLatch(CountDownLatch recievedLatch) {
        if (mRecievedLatch == null)
        {
            mRecievedLatch = recievedLatch;
        }
    }

    @Override
    public CountDownLatch getRecievedLatch() {
        return mRecievedLatch;
    }

    @Override
    public void setMessage(String message) {
        mMessage = message;
    }

    @Override
    public String getMessage() {
        return mMessage;
    }

    /*
     * Implements PhotoDownloadRunnable.handleHTTPState(). Passes the download state to the
     * ThreadPool object.
     */

    @Override
    public void handleWebSocketState(int state) {
        int outState;

        // Converts the download state to the overall state
        switch(state) {
            case WebSocketRunnable.WEBSOCKET_STATE_FAILED:
                outState = WebSocketManager.WEBSOCKET_CONNECT_FAIL;
                break;
            case WebSocketRunnable.WEBSOCKET_STATE_STARTED:
                outState = WebSocketManager.WEBSOCKET_CONNECT_SUCCESS;
                break;
            case WebSocketRunnable.RECORDER_STOPED:
                outState = WebSocketManager.RECORDER_STOPED;
                break;
            case WebSocketRunnable.RECORDER_STARTED:
                outState = WebSocketManager.RECORDER_STARTED;
                break;
            default:
                outState = WebSocketManager.WEBSOCKET_TEXT;
                break;
        }
        // Passes the state to the ThreadPool object.
        handleState(outState);
    }

}
