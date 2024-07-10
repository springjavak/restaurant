package com.restaurant;

import java.time.Duration;

public interface Manager {
    // new client(s) show up
    void onArrive(ClientsGroup group);

    // client(s) leave, either served or simply abandoning the queue
    void onLeave(ClientsGroup group);

    // return table where a given client group is seated,
    // or null if it is still queueing or has already left
    Table lookup(ClientsGroup group);

    boolean abandonQueue(ClientsGroup group);

    boolean abandonQueueIf(ClientsGroup group, Duration waitLimit);

    boolean abandonAllIf(Duration waitLimit);

    // testing methods
    default int getQueueCount() {
        return -1;
    }

    default int getSeatCount() {
        return -1;
    }
}
