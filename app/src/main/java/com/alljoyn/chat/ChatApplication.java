package com.alljoyn.chat;

import com.alljoyn.chat.AllJoynService;
import com.alljoyn.chat.Observable;
import com.alljoyn.chat.Observer;
import com.alljoyn.chat.AllJoynService.UseChannelState;

import android.app.Application;

import android.content.ComponentName;
import android.content.Intent;

import android.util.Log;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.Date;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class ChatApplication extends Application implements Observable {
    private static final String TAG = "chat.ChatApplication";
    public static String PACKAGE_NAME;
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
        PACKAGE_NAME = getApplicationContext().getPackageName();
        Intent intent = new Intent(this, AllJoynService.class);
        mRunningService = startService(intent);
        if (mRunningService == null) {
            Log.i(TAG, "onCreate(): failed to startService()");
        }
    }

    ComponentName mRunningService = null;

    public void quit() {
        notifyObservers(APPLICATION_QUIT_EVENT);
        mRunningService = null;
    }

    public static final String APPLICATION_QUIT_EVENT = "APPLICATION_QUIT_EVENT";

    public synchronized void alljoynError(Module m, String s) {
        mModule = m;
        mErrorString = s;
        notifyObservers(ALLJOYN_ERROR_EVENT);
    }
    public Module getErrorModule() {
        return mModule;
    }
    private Module mModule = Module.NONE;
    public static enum Module {
        NONE,
        GENERAL,
        USE,
        HOST
    }

    public String getErrorString() {
        return mErrorString;
    }
    private String mErrorString = "ER_OK";

    public static final String ALLJOYN_ERROR_EVENT = "ALLJOYN_ERROR_EVENT";

    public synchronized void addFoundChannel(String channel) {
        Log.i(TAG, "addFoundChannel(" + channel + ")");
        removeFoundChannel(channel);
        mChannels.add(channel);
        Log.i(TAG, "addFoundChannel(): added " + channel);
    }
    public synchronized void removeFoundChannel(String channel) {
        Log.i(TAG, "removeFoundChannel(" + channel + ")");

        for (Iterator<String> i = mChannels.iterator(); i.hasNext();) {
            String string = i.next();
            if (string.equals(channel)) {
                Log.i(TAG, "removeFoundChannel(): removed " + channel);
                i.remove();
            }
        }
    }
    public synchronized List<String> getFoundChannels() {
        Log.i(TAG, "getFoundChannels()");
        List<String> clone = new ArrayList<String>(mChannels.size());
        for (String string : mChannels) {
            Log.i(TAG, "getFoundChannels(): added " + string);
            clone.add(new String(string));
        }
        return clone;
    }
    private List<String> mChannels = new ArrayList<String>();

    public synchronized void hostSetChannelState(AllJoynService.HostChannelState state) {
        mHostChannelState = state;
        notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
    }
    public synchronized AllJoynService.HostChannelState hostGetChannelState() {
        return mHostChannelState;
    }
    private AllJoynService.HostChannelState mHostChannelState = AllJoynService.HostChannelState.IDLE;


    public synchronized void hostSetUserName(String name) {
        mUserName = name;
        notifyObservers(HOST_USER_STATE_CHANGED_EVENT);
    }
    public synchronized String hostGetUserName() {
        return mUserName;
    }
    private String mUserName;

    public static final String HOST_USER_STATE_CHANGED_EVENT = "HOST_USER_STATE_CHANGED_EVENT";

    public synchronized void hostSetChannelName(String name) {
        mHostChannelName = name;
        notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
    }
    public synchronized String hostGetChannelName() {
        return mHostChannelName;
    }
    private String mHostChannelName;

    public static final String HOST_CHANNEL_STATE_CHANGED_EVENT = "HOST_CHANNEL_STATE_CHANGED_EVENT";

    public synchronized void useSetChannelState(AllJoynService.UseChannelState state) {
        mUseChannelState = state;
        notifyObservers(USE_CHANNEL_STATE_CHANGED_EVENT);
    }
    public synchronized AllJoynService.UseChannelState useGetChannelState() {
        return mUseChannelState;
    }
    private AllJoynService.UseChannelState mUseChannelState = AllJoynService.UseChannelState.IDLE;

    private String mUseChannelName;
    public synchronized void useSetChannelName(String name) {
        mUseChannelName = name;
        notifyObservers(USE_CHANNEL_STATE_CHANGED_EVENT);
    }
    public synchronized String useGetChannelName() {
        return mUseChannelName;
    }

    public static final String USE_CHANNEL_STATE_CHANGED_EVENT = "USE_CHANNEL_STATE_CHANGED_EVENT";

    public synchronized void useJoinChannel() {
        clearHistory();
        notifyObservers(USE_CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(USE_JOIN_CHANNEL_EVENT);
    }
    public static final String USE_JOIN_CHANNEL_EVENT = "USE_JOIN_CHANNEL_EVENT";

    public synchronized void useLeaveChannel() {
        notifyObservers(USE_CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(USE_LEAVE_CHANNEL_EVENT);
    }
    public static final String USE_LEAVE_CHANNEL_EVENT = "USE_LEAVE_CHANNEL_EVENT";

    public synchronized void hostInitChannel() {
        notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(HOST_INIT_CHANNEL_EVENT);
    }
    public static final String HOST_INIT_CHANNEL_EVENT = "HOST_INIT_CHANNEL_EVENT";

    public synchronized void hostStartChannel() {
        notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(HOST_START_CHANNEL_EVENT);
    }
    public static final String HOST_START_CHANNEL_EVENT = "HOST_START_CHANNEL_EVENT";


    public synchronized void hostStopChannel() {
        notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(HOST_STOP_CHANNEL_EVENT);
    }
    public static final String HOST_STOP_CHANNEL_EVENT = "HOST_STOP_CHANNEL_EVENT";

    public synchronized void newLocalUserMessage(String message) {
        addInboundItem("Me", message);
        if (useGetChannelState() == AllJoynService.UseChannelState.JOINED) {
            addOutboundItem(hostGetUserName() + "%%%" + message);
        }
    }

    public synchronized void newRemoteUserMessage(String nickname, String message) {
        addInboundItem(nickname, message);
    }

    final int OUTBOUND_MAX = 5;

    public static final String OUTBOUND_CHANGED_EVENT = "OUTBOUND_CHANGED_EVENT";

    private List<String> mOutbound = new ArrayList<String>();
    private void addOutboundItem(String message) {
        if (mOutbound.size() == OUTBOUND_MAX) {
            mOutbound.remove(0);
        }
        mOutbound.add(message);
        notifyObservers(OUTBOUND_CHANGED_EVENT);
    }
    public synchronized String getOutboundItem() {
        if (mOutbound.isEmpty()) {
            return null;
        } else {
            return mOutbound.remove(0);
        }
    }

    public static final String HISTORY_CHANGED_EVENT = "HISTORY_CHANGED_EVENT";

    private void addInboundItem(String nickname, String message) {
        addHistoryItem(nickname, message);
    }

    final int HISTORY_MAX = 20;

    private List<String> mHistory = new ArrayList<String>();
    private void addHistoryItem(String nickname, String message) {
        if (mHistory.size() == HISTORY_MAX) {
            mHistory.remove(0);
        }
        DateFormat dateFormat = new SimpleDateFormat("HH:mm");
        Date date = new Date();
        mHistory.add("[" + dateFormat.format(date) + "] (" + nickname + ") " + message);
        notifyObservers(HISTORY_CHANGED_EVENT);
    }
    private void clearHistory() {
        mHistory.clear();
        notifyObservers(HISTORY_CHANGED_EVENT);
    }
    public synchronized List<String> getHistory() {
        List<String> clone = new ArrayList<String>(mHistory.size());
        for (String string : mHistory) {
            clone.add(new String(string));
        }
        return clone;
    }

    public synchronized void addObserver(Observer obs) {
        Log.i(TAG, "addObserver(" + obs + ")");
        if (mObservers.indexOf(obs) < 0) {
            mObservers.add(obs);
        }
    }

    public synchronized void deleteObserver(Observer obs) {
        Log.i(TAG, "deleteObserver(" + obs + ")");
        mObservers.remove(obs);
    }

    private void notifyObservers(Object arg) {
        Log.i(TAG, "notifyObservers(" + arg + ")");
        for (Observer obs : mObservers) {
            Log.i(TAG, "notify observer = " + obs);
            obs.update(this, arg);
        }
    }

    private List<Observer> mObservers = new ArrayList<Observer>();
}
