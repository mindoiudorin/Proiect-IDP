package com.alljoyn.chat;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LobyChannels extends Activity implements Observer{

    private static final String TAG = "chat.LobyChannels";
    private ChatApplication mChatApplication = null;
    private TextView mUserName;
    private TextView mHostChannelName;
    private TextView mHostChannelStatus;
    private ListView myChannelList;
    private Thread updateChannelThread;
    private ExecutorService executor;
    List<String> connectedChannel = null;
    private boolean channelSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loby_channels);
        String channel = getIntent().getStringExtra("channel");
        String user = getIntent().getStringExtra("user");

        mUserName = (TextView) findViewById(R.id.userNameLobby);
        mHostChannelName = (TextView) findViewById(R.id.channelHostNameLobby);
        mHostChannelStatus = (TextView) findViewById(R.id.channelHostStatusLobby);
        myChannelList = (ListView) findViewById(R.id.myChannelList);
        connectedChannel = new ArrayList<>();

        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.addObserver(this);
        mChatApplication.hostSetChannelName(channel);
        mChatApplication.hostSetUserName(user);
        mChatApplication.hostInitChannel();

        updateChannelStateHost();

        startChannelUpdateThread();
        executor = Executors.newFixedThreadPool(1);
        executor.submit(updateChannelThread);

        myChannelList.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    myChannelList.getChildAt(position).setBackgroundColor(Color.LTGRAY);
                    String name = myChannelList.getItemAtPosition(position).toString();
                    connectedChannel.add(name);
                }
                catch (Exception e) {
                    Log.e(TAG, "setOnItemClickListener(): ERROR SELECT CHANNEL");
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.deleteObserver(this);
        mChatApplication.hostStopChannel();
        executor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        connectedChannel.clear();
    }

    public void JoinChannelButtonClicked(View view) {
        Log.i(TAG, "createUseJoinDialog()");
        if(connectedChannel.size() == 0) {
            Toast warningToast = Toast.makeText(this, "Channel was NOT selected ", Toast.LENGTH_SHORT);
            warningToast.setGravity(Gravity.TOP, 0, 120);
            warningToast.show();
            return;
        }
        String name = connectedChannel.get(0);// = channelList.getItemAtPosition(position).toString();
        mChatApplication.useSetChannelName(name);
        mChatApplication.useJoinChannel();
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra("channel", name);
        intent.putExtra("user", mUserName.getText());
        startActivity(intent);
    }

    public void AdvertiseChannelButtonClicked(View view) {
        if(!channelSet) {
            mChatApplication.hostStartChannel();
            Button button = (Button) view;
            button.setText("Stop Advertise Channel");
            channelSet = true;
        }
        else {
            mChatApplication.hostStopChannel();
            Button button = (Button) view;
            button.setText("Start Advertise Channel");
            channelSet = false;
        }
    }

    public void ResetChannelButtonClicked(View view) {
        finish();
    }

    private void addChannelsToList() {
        connectedChannel.clear();
        ArrayAdapter<String> channelListAdapter = new ArrayAdapter<String>(this, android.R.layout.test_list_item);
        myChannelList.setAdapter(channelListAdapter);

        List<String> channels = mChatApplication.getFoundChannels();
        for (String channel : channels) {
            int lastDot = channel.lastIndexOf('.');
            if (lastDot < 0) {
                continue;
            }
            channelListAdapter.add(channel.substring(lastDot + 1));
        }
        channelListAdapter.notifyDataSetChanged();
    }

    private void startChannelUpdateThread() {
        updateChannelThread = new Thread() {
            @Override
            public void run() {
                try {
                    while(true) {
                        sleep(5000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addChannelsToList();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        updateChannelThread.start();
    }

    private void updateChannelStateHost() {
        AllJoynService.HostChannelState channelState = mChatApplication.hostGetChannelState();
        String name1 = mChatApplication.hostGetUserName();
        if (name1 == null) {
            name1 = "Not set";
        }
        try {
            mUserName.setText(name1);
        } catch (Exception e) {
            Log.e(TAG, "updateChannelStateHost(): ERROR SET USER NAME");
            Log.e(TAG, e.getLocalizedMessage());
        }

        String name2 = mChatApplication.hostGetChannelName();
        if (name2 == null) {
            name2 = "Not set";
        }
        try {
            mHostChannelName.setText(name2);
        }
        catch (Exception e) {
            Log.e(TAG, "updateChannelStateHost(): ERROR SET CHANNEL NAME");
            Log.e(TAG, e.getLocalizedMessage());
        }
        try {
            switch (channelState) {
                case IDLE:
                    mHostChannelStatus.setText("Idle");
                    break;
                case NAMED:
                    mHostChannelStatus.setText("Named");
                    break;
                case BOUND:
                    mHostChannelStatus.setText("Bound");
                    break;
                case ADVERTISED:
                    mHostChannelStatus.setText("Advertised");
                    break;
                case CONNECTED:
                    mHostChannelStatus.setText("Connected");
                    break;
                default:
                    mHostChannelStatus.setText("Unknown");
                    break;
            }
        }
        catch (Exception e) {
            Log.e(TAG, "updateChannelStateHost(): ERROR SET CHANNEL STATE: " + channelState);
            Log.e(TAG, e.getLocalizedMessage());
        }
    }


    private void alljoynError() {
        if (mChatApplication.getErrorModule() == ChatApplication.Module.GENERAL ||
                mChatApplication.getErrorModule() == ChatApplication.Module.USE) {
            Log.i(TAG, "createAllJoynErrorDialog()");
            final Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(dialog.getWindow().FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.alljoynerrordialog);

            TextView errorText = (TextView)dialog.findViewById(R.id.errorDescription);
            errorText.setText(mChatApplication.getErrorString());

            Button yes = (Button)dialog.findViewById(R.id.errorOk);
            yes.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    dialog.cancel();
                }
            });
        }
    }

    public synchronized void update(Observable o, final Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            String qualifier = (String)arg;
                if (qualifier.equals(ChatApplication.APPLICATION_QUIT_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): APPLICATION_QUIT_EVENT");
                    finish();
                }

                if (qualifier.equals(ChatApplication.HOST_CHANNEL_STATE_CHANGED_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): HOST_CHANNEL_STATE_CHANGED_EVENT");
                    updateChannelStateHost();
                }

                if (qualifier.equals(ChatApplication.ALLJOYN_ERROR_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): ALLJOYN_ERROR_EVENT");
                    alljoynError();
                }
            }
        });
    }
}
