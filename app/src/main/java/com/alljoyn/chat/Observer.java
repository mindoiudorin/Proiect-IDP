package com.alljoyn.chat;

import com.alljoyn.chat.Observable;

public interface Observer {
    public void update(Observable o, Object arg);
}
