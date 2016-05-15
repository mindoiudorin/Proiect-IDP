package com.alljoyn.chat;

import android.app.Activity;
import android.app.Dialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class ChatActivity extends Activity implements Observer{

    private static final String TAG = "chat.ChatActivity";
    private TextView mUserName;
    private TextView mInUseChannelName;
    private TextView mInUseChannelStatus;
    private ArrayAdapter<String> mHistoryList;
    private ChatApplication mChatApplication = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        String channel = getIntent().getStringExtra("channel");
        String user = getIntent().getStringExtra("user");

        mUserName = (TextView) findViewById(R.id.userNameChat);
        mInUseChannelName = (TextView) findViewById(R.id.channelInUseNameLobby);
        mInUseChannelStatus = (TextView) findViewById(R.id.channelInUseStatusLobby);

        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.addObserver(this);

        //mChatApplication.useSetChannelName(channel);
        //mChatApplication.useJoinChannel();

        mHistoryList = new ArrayAdapter<String>(this, android.R.layout.test_list_item);
        ListView hlv = (ListView) findViewById(R.id.useHistoryList2);
        hlv.setAdapter(mHistoryList);

        EditText messageBox = (EditText)findViewById(R.id.useMessage2);
        messageBox.setSingleLine();
        messageBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String message = view.getText().toString();
                    Log.i(TAG, "useMessage.onEditorAction(): got message " + message + ")");
                    mChatApplication.newLocalUserMessage(message);
                    view.setText("");
                }
                return true;
            }
        });

        updateChannelStateUse();
        updateHistory();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.deleteObserver(this);
        super.onDestroy();
    }

    public void LeaveChannelButtonClicked(View view) {
        mChatApplication.useLeaveChannel();
        mChatApplication.useSetChannelName("Not set");
        finish();
    }


    private void updateHistory() {
        Log.i(TAG, "updateHistory()");
        mHistoryList.clear();
        List<String> messages = mChatApplication.getHistory();
        for (String message : messages) {
            mHistoryList.add(message);
        }
        mHistoryList.notifyDataSetChanged();
    }

    private void updateChannelStateUse() {
        Log.i(TAG, "updateHistory()");
        AllJoynService.UseChannelState channelState = mChatApplication.useGetChannelState();
        String name = mChatApplication.hostGetUserName();
        if (name == null) {
            name = "Not set";
        }
        try {
            mUserName.setText(name);
        } catch (Exception e) {
            Log.e(TAG, "updateChannelStateHost(): ERROR SET USER NAME");
            Log.e(TAG, e.getLocalizedMessage());
        }
        name = mChatApplication.useGetChannelName();
        if (name == null) {
            name = "Not set";
        }
        mInUseChannelName.setText(name);

        switch (channelState) {
            case IDLE:
                mInUseChannelStatus.setText("Idle");
                break;
            case JOINED:
                mInUseChannelStatus.setText("Joined");
                break;
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
                String qualifier = (String) arg;
                if (qualifier.equals(ChatApplication.APPLICATION_QUIT_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): APPLICATION_QUIT_EVENT");
                    finish();
                }

                if (qualifier.equals(ChatApplication.HISTORY_CHANGED_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): HANDLE_HISTORY_CHANGED_EVENT");
                    updateHistory();
                }

                if (qualifier.equals(ChatApplication.USE_CHANNEL_STATE_CHANGED_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): HANDLE_CHANNEL_STATE_CHANGED_EVENT");
                    updateChannelStateUse();
                }

                if (qualifier.equals(ChatApplication.ALLJOYN_ERROR_EVENT)) {
                    Log.i(TAG, "runOnUiThread(): ALLJOYN_ERROR_EVENT");
                    alljoynError();
                }
            }
        });
    }

}
