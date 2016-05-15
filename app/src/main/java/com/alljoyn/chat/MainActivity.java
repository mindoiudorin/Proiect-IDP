package com.alljoyn.chat;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements Observer{

    private static final String TAG = "chat.MainActivity";
    private ChatApplication mChatApplication = null;
    private TextView userNameTextView = null;
    private TextView channelNameTextView = null;
    private Toast warningToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        userNameTextView = (TextView) findViewById(R.id.userName);
        channelNameTextView = (TextView) findViewById(R.id.channelName);
        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.addObserver(this);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mChatApplication = (ChatApplication)getApplication();
        mChatApplication.deleteObserver(this);
        super.onDestroy();
    }

    public void continueButtonClicked(View view) {
        String user = userNameTextView.getText().toString();
        String channel = channelNameTextView.getText().toString();
        if(user.length() == 0 || channel.length() == 0){
            //warningToast.cancel();
            warningToast = Toast.makeText(this, "Username or Channel Name \n\t\t\t\t\t\t NOT SET ", Toast.LENGTH_SHORT);
            warningToast.setGravity(Gravity.TOP, 0, 120);
            warningToast.show();
            return;
        }
        Intent intent = new Intent(getApplicationContext(), LobyChannels.class);
        intent.putExtra("channel", channel);
        intent.putExtra("user", user);
        startActivity(intent);
    }

    public void exitButtonClicked(View view) {
        mChatApplication.quit();
    }

    public synchronized void update(Observable o, final Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String qualifier = (String) arg;

                if (qualifier.equals(ChatApplication.APPLICATION_QUIT_EVENT)) {
                    finish();
                }
            }
        });
    }
}
