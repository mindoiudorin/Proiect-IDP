package com.alljoyn.chat;

import com.alljoyn.chat.Observer;

public interface Observable {
    public void addObserver(Observer obs);
    public void deleteObserver(Observer obs);
}
