package com.restaurant;

public final class YieldOnWait extends ActionOnWait {

    @Override
    public void actOnWait() {
        Thread.yield();
    }

}
