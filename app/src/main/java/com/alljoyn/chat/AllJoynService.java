package com.alljoyn.chat;

import com.alljoyn.chat.ChatApplication;
import com.alljoyn.chat.Observable;
import com.alljoyn.chat.Observer;
import com.alljoyn.chat.ChatInterface;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.MessageContext;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

import android.os.AsyncTask;
import android.os.IBinder;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Intent;

import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class AllJoynService extends Service implements Observer {
    private static final String TAG = "chat.AllJoynService";

    /**
     * We don't use the bindery to communiate between any client and this
     * service so we return null.
     */
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return null;
    }

    public void onCreate() {
        Log.i(TAG, "onCreate()");
        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.addObserver(this);

        CharSequence title = "AllJoyn";
        CharSequence message = "Chat Channel Hosting Service.";
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        Notification notification = builder.setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.icon).setTicker(message).setWhen(System.currentTimeMillis())
                .setAutoCancel(true).setContentTitle(title)
                .setContentText(message).build();

        notification.flags |= Notification.DEFAULT_SOUND | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        Log.i(TAG, "onCreate(): startForeground()");
        startForeground(NOTIFICATION_ID, notification);

        doConnect();
        doStartDiscovery();
    }

    private static final int NOTIFICATION_ID = 0xdefaced;

    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        doStopDiscovery();
        doDisconnect();
        mChatApplication.deleteObserver(this);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");
        return START_STICKY;
    }

    /**
     * A reference to a descendent of the Android Application class that is
     * acting as the Model of our MVC-based application.
     */
    private ChatApplication mChatApplication = null;

    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        final String qualifier = (String)arg;
        AsyncTask.execute(new Runnable() {
            public void run() {
                if (qualifier.equals(ChatApplication.APPLICATION_QUIT_EVENT)) {
                    doLeaveSession();
                    doCancelAdvertise();
                    doUnbindSession();
                    doReleaseName();
                    stopSelf();
                }
                if (qualifier.equals(ChatApplication.USE_JOIN_CHANNEL_EVENT)) {
                    doJoinSession();
                }
                if (qualifier.equals(ChatApplication.USE_LEAVE_CHANNEL_EVENT)) {
                    doLeaveSession();
                }
                if (qualifier.equals(ChatApplication.HOST_INIT_CHANNEL_EVENT)) {
                }
                if (qualifier.equals(ChatApplication.HOST_START_CHANNEL_EVENT)) {
                    doRequestName();
                    doBindSession();
                    doAdvertise();
                }
                if (qualifier.equals(ChatApplication.HOST_STOP_CHANNEL_EVENT)) {
                    doCancelAdvertise();
                    doUnbindSession();
                    doReleaseName();
                }
                if (qualifier.equals(ChatApplication.OUTBOUND_CHANGED_EVENT)) {
                    doSendMessages();
                }

            }
        });
    }

    public static enum BusAttachmentState {
        DISCONNECTED,    /** The bus attachment is not connected to the AllJoyn bus */
        CONNECTED,        /** The  bus attachment is connected to the AllJoyn bus */
        DISCOVERING        /** The bus attachment is discovering remote attachments hosting chat channels */
    }

    /**
     * The state of the AllJoyn bus attachment.
     */
    private BusAttachmentState mBusAttachmentState = BusAttachmentState.DISCONNECTED;


    public static enum HostChannelState {
        IDLE,            /** There is no hosted chat channel */
        NAMED,            /** The well-known name for the channel has been successfully acquired */
        BOUND,            /** A session port has been bound for the channel */
        ADVERTISED,        /** The bus attachment has advertised itself as hosting an chat channel */
        CONNECTED       /** At least one remote device has connected to a session on the channel */
    }

    /**
     * The state of the AllJoyn components responsible for hosting an chat channel.
     */
    private HostChannelState mHostChannelState = HostChannelState.IDLE;

    public static enum UseChannelState {
        IDLE,            /** There is no used chat channel */
        JOINED,            /** The session for the channel has been successfully joined */
    }

    /**
     * The state of the AllJoyn components responsible for hosting an chat channel.
     */
    private UseChannelState mUseChannelState = UseChannelState.IDLE;


    private BusAttachment mBus  = new BusAttachment(ChatApplication.PACKAGE_NAME, BusAttachment.RemoteMessage.Receive);

    /**
     * The well-known name prefix which all bus attachments hosting a channel
     * will use.  The NAME_PREFIX and the channel name are composed to give
     * the well-known name a hosting bus attachment will request and
     * advertise.
     */
    private static final String NAME_PREFIX = "org.alljoyn.bus.samples.chat";

    /**
     * The well-known session port used as the contact port for the chat service.
     */
    private static final short CONTACT_PORT = 27;

    /**
     * The object path used to identify the service "location" in the bus
     * attachment.
     */
    private static final String OBJECT_PATH = "/chatService";

    /**
     * The ChatBusListener is a class that listens to the AllJoyn bus for
     * notifications corresponding to the existence of events happening out on
     * the bus.  We provide one implementation of our listener to the bus
     * attachment during the connect().
     */
    private class ChatBusListener extends BusListener {

        public void foundAdvertisedName(String name, short transport, String namePrefix) {
            Log.i(TAG, "mBusListener.foundAdvertisedName(" + name + ")");
            ChatApplication application = (ChatApplication)getApplication();
            application.addFoundChannel(name);
        }


        public void lostAdvertisedName(String name, short transport, String namePrefix) {
            Log.i(TAG, "mBusListener.lostAdvertisedName(" + name + ")");
            ChatApplication application = (ChatApplication)getApplication();
            application.removeFoundChannel(name);
        }
    }

    /**
     * An instance of an AllJoyn bus listener that knows what to do with
     * foundAdvertisedName and lostAdvertisedName notifications.  Although
     * we often use the anonymous class idiom when talking to AllJoyn, the
     * bus listener works slightly differently and it is better to use an
     * explicitly declared class in this case.
     */
    private ChatBusListener mBusListener = new ChatBusListener();

    private void doConnect() {
        Log.i(TAG, "doConnect()");
        org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
        assert(mBusAttachmentState == BusAttachmentState.DISCONNECTED);
        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);
        mBus.registerBusListener(mBusListener);
        Status status = mBus.registerBusObject(mChatService, OBJECT_PATH);
        if (Status.OK != status) {
            mChatApplication.alljoynError(ChatApplication.Module.HOST, "Unable to register the chat bus object: (" + status + ")");
            return;
        }

        status = mBus.connect();
        if (status != Status.OK) {
            mChatApplication.alljoynError(ChatApplication.Module.GENERAL, "Unable to connect to the bus: (" + status + ")");
            return;
        }

        status = mBus.registerSignalHandlers(this);
        if (status != Status.OK) {
            mChatApplication.alljoynError(ChatApplication.Module.GENERAL, "Unable to register signal handlers: (" + status + ")");
            return;
        }

        mBusAttachmentState = BusAttachmentState.CONNECTED;
    }

    private boolean doDisconnect() {
        Log.i(TAG, "doDisonnect()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        mBus.unregisterBusListener(mBusListener);
        mBus.disconnect();
        mBusAttachmentState = BusAttachmentState.DISCONNECTED;
        return true;
    }

    private void doStartDiscovery() {
        Log.i(TAG, "doStartDiscovery()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        Status status = mBus.findAdvertisedName(NAME_PREFIX);
        if (status == Status.OK) {
            mBusAttachmentState = BusAttachmentState.DISCOVERING;
            return;
        } else {
            mChatApplication.alljoynError(ChatApplication.Module.USE, "Unable to start finding advertised names: (" + status + ")");
            return;
        }
    }

    private void doStopDiscovery() {
        Log.i(TAG, "doStopDiscovery()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        mBus.cancelFindAdvertisedName(NAME_PREFIX);
        mBusAttachmentState = BusAttachmentState.CONNECTED;
    }

    private void doRequestName() {
        Log.i(TAG, "doRequestName()");
        int stateRelation = mBusAttachmentState.compareTo(BusAttachmentState.DISCONNECTED);
        assert (stateRelation >= 0);
        String wellKnownName = NAME_PREFIX + "." + mChatApplication.hostGetChannelName();
        Status status = mBus.requestName(wellKnownName, BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE);
        if (status == Status.OK) {
            mHostChannelState = HostChannelState.NAMED;
            mChatApplication.hostSetChannelState(mHostChannelState);
        } else {
            mChatApplication.alljoynError(ChatApplication.Module.USE, "Unable to acquire well-known name: (" + status + ")");
        }
    }

    private void doReleaseName() {
        Log.i(TAG, "doReleaseName()");
        int stateRelation = mBusAttachmentState.compareTo(BusAttachmentState.DISCONNECTED);
        assert (stateRelation >= 0);
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED || mBusAttachmentState == BusAttachmentState.DISCOVERING);
        assert(mHostChannelState == HostChannelState.NAMED);
        String wellKnownName = NAME_PREFIX + "." + mChatApplication.hostGetChannelName();
        mBus.releaseName(wellKnownName);
        mHostChannelState = HostChannelState.IDLE;
        mChatApplication.hostSetChannelState(mHostChannelState);
    }

    private void doBindSession() {
        Log.i(TAG, "doBindSession()");

        Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);

        Status status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
            public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                Log.i(TAG, "SessionPortListener.acceptSessionJoiner(" + sessionPort + ", " + joiner + ", " + sessionOpts.toString() + ")");
                if (sessionPort == CONTACT_PORT) {
                    return true;
                }
                return false;
            }
            public void sessionJoined(short sessionPort, int id, String joiner) {
                Log.i(TAG, "SessionPortListener.sessionJoined(" + sessionPort + ", " + id + ", " + joiner + ")");
                mHostSessionId = id;
                SignalEmitter emitter = new SignalEmitter(mChatService, id, SignalEmitter.GlobalBroadcast.Off);
                mHostChatInterface = emitter.getInterface(ChatInterface.class);
            }
        });

        if (status == Status.OK) {
            mHostChannelState = HostChannelState.BOUND;
            mChatApplication.hostSetChannelState(mHostChannelState);
        } else {
            mChatApplication.alljoynError(ChatApplication.Module.HOST, "Unable to bind session contact port: (" + status + ")");
            return;
        }
    }

    private void doUnbindSession() {
        Log.i(TAG, "doUnbindSession()");
        mBus.unbindSessionPort(CONTACT_PORT);
        mHostChatInterface = null;
        mHostChannelState = HostChannelState.NAMED;
        mChatApplication.hostSetChannelState(mHostChannelState);
    }

    /**
     * The session identifier of the "host" session that the application
     * provides for remote devices.  Set to -1 if not connected.
     */
    int mHostSessionId = -1;

    /**
     * A flag indicating that the application has joined a chat channel that
     * it is hosting.  See the long comment in doJoinSession() for a
     * description of this rather non-intuitively complicated case.
     */
    boolean mJoinedToSelf = false;

    /**
     * This is the interface over which the chat messages will be sent in
     * the case where the application is joined to a chat channel hosted
     * by the application.  See the long comment in doJoinSession() for a
     * description of this rather non-intuitively complicated case.
     */
    ChatInterface mHostChatInterface = null;

    private void doAdvertise() {
        Log.i(TAG, "doAdvertise()");
        String wellKnownName = NAME_PREFIX + "." + mChatApplication.hostGetChannelName();
        Status status = mBus.advertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

        if (status == Status.OK) {
            mHostChannelState = HostChannelState.ADVERTISED;
            mChatApplication.hostSetChannelState(mHostChannelState);
        } else {
            mChatApplication.alljoynError(ChatApplication.Module.HOST, "Unable to advertise well-known name: (" + status + ")");
            return;
        }
    }

    private void doCancelAdvertise() {
        Log.i(TAG, "doCancelAdvertise()");
        String wellKnownName = NAME_PREFIX + "." + mChatApplication.hostGetChannelName();
        Status status = mBus.cancelAdvertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

        if (status != Status.OK) {
            mChatApplication.alljoynError(ChatApplication.Module.HOST, "Unable to cancel advertisement of well-known name: (" + status + ")");
            return;
        }
        mHostChannelState = HostChannelState.BOUND;
        mChatApplication.hostSetChannelState(mHostChannelState);
    }

    private void doJoinSession() {
        Log.i(TAG, "doJoinSession()");
        if (mHostChannelState != HostChannelState.IDLE) {
            if (mChatApplication.useGetChannelName().equals(mChatApplication.hostGetChannelName())) {
                mUseChannelState = UseChannelState.JOINED;
                mChatApplication.useSetChannelState(mUseChannelState);
                mJoinedToSelf = true;
                return;
            }
        }
        String wellKnownName = NAME_PREFIX + "." + mChatApplication.useGetChannelName();
        short contactPort = CONTACT_PORT;
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);
        Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

        Status status = mBus.joinSession(wellKnownName, contactPort, sessionId, sessionOpts, new SessionListener() {
            /**
             * This method is called when the last remote participant in the
             * chat session leaves for some reason and we no longer have anyone
             * to chat with.
             *
             * In the class documentation for the BusListener note that it is a
             * requirement for this method to be multithread safe.  This is
             * accomplished by the use of a monitor on the ChatApplication as
             * exemplified by the synchronized attribute of the removeFoundChannel
             * method there.
             */
            public void sessionLost(int sessionId, int reason) {
                Log.i(TAG, "BusListener.sessionLost(sessionId=" + sessionId + ",reason=" + reason + ")");
                mChatApplication.alljoynError(ChatApplication.Module.USE, "The chat session has been lost");
                mUseChannelState = UseChannelState.IDLE;
                mChatApplication.useSetChannelState(mUseChannelState);
            }
        });

        if (status == Status.OK) {
            Log.i(TAG, "doJoinSession(): use sessionId is " + mUseSessionId);
            mUseSessionId = sessionId.value;
        } else {
            mChatApplication.alljoynError(ChatApplication.Module.USE, "Unable to join chat session: (" + status + ")");
            return;
        }

        SignalEmitter emitter = new SignalEmitter(mChatService, mUseSessionId, SignalEmitter.GlobalBroadcast.Off);
        mChatInterface = emitter.getInterface(ChatInterface.class);

        mUseChannelState = UseChannelState.JOINED;
        mChatApplication.useSetChannelState(mUseChannelState);
    }

    /**
     * This is the interface over which the chat messages will be sent.
     */
    ChatInterface mChatInterface = null;

    /**
     * Implementation of the functionality related to joining an existing
     * remote session.
     */
    private void doLeaveSession() {
        Log.i(TAG, "doLeaveSession()");
        if (mJoinedToSelf == false) {
            mBus.leaveSession(mUseSessionId);
        }
        mUseSessionId = -1;
        mJoinedToSelf = false;
        mUseChannelState = UseChannelState.IDLE;
        mChatApplication.useSetChannelState(mUseChannelState);
    }

    /**
     * The session identifier of the "use" session that the application
     * uses to talk to remote instances.  Set to -1 if not connectecd.
     */
    int mUseSessionId = -1;

    private void doSendMessages() {
        Log.i(TAG, "doSendMessages()");

        String message;
        while ((message = mChatApplication.getOutboundItem()) != null) {
            Log.i(TAG, "doSendMessages(): sending message \"" + message + "\"");
            try {
                if (mJoinedToSelf) {
                    if (mHostChatInterface != null) {
                        mHostChatInterface.Chat(message);
                    }
                } else {
                    mChatInterface.Chat(message);
                }
            } catch (BusException ex) {
                mChatApplication.alljoynError(ChatApplication.Module.USE, "Bus exception while sending message: (" + ex + ")");
            }
        }
    }

    /**
     * Our chat messages are going to be Bus Signals multicast out onto an
     * associated session.  In order to send signals, we need to define an
     * AllJoyn bus object that will allow us to instantiate a signal emmiter.
     */
    class ChatService implements ChatInterface, BusObject {
        /**
         * Intentionally empty implementation of Chat method.  Since this
         * method is only used as a signal emitter, it will never be called
         * directly.
         */
        public void Chat(String str) throws BusException {
        }
    }

    /**
     * The ChatService is the instance of an AllJoyn interface that is exported
     * on the bus and allows us to send signals implementing messages
     */
    private ChatService mChatService = new ChatService();

    /**
     * The signal handler for messages received from the AllJoyn bus.
     *
     * Since the messages sent on a chat channel will be sent using a bus
     * signal, we need to provide a signal handler to receive those signals.
     * This is it.  Note that the name of the signal handler has the first
     * letter capitalized to conform with the DBus convention for signal
     * handler names.
     */
    @BusSignalHandler(iface = "org.alljoyn.bus.samples.chat", signal = "Chat")
    public void Chat(String string) {
        String uniqueName = mBus.getUniqueName();
        MessageContext ctx = mBus.getMessageContext();
        Log.i(TAG, "Chat(): use sessionId is " + mUseSessionId);
        Log.i(TAG, "Chat(): message sessionId is " + ctx.sessionId);
        if (ctx.sender.equals(uniqueName)) {
            Log.i(TAG, "Chat(): dropped our own signal received on session " + ctx.sessionId);
            return;
        }
        if (mJoinedToSelf == false && ctx.sessionId == mHostSessionId) {
            Log.i(TAG, "Chat(): dropped signal received on hosted session " + ctx.sessionId + " when not joined-to-self");
            return;
        }
        String nickname = ctx.sender;
        nickname = nickname.substring(nickname.length()-10, nickname.length());

        String[] temp = string.split("%%%");
        nickname = temp[0];
        string = temp[1];

        Log.i(TAG, "Chat(): signal " + string + " received from nickname " + nickname);
        mChatApplication.newRemoteUserMessage(nickname, string);
    }
    static {
        Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
        System.loadLibrary("alljoyn_java");
    }
}
