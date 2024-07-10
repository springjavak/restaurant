package com.restaurant;

public final class YieldOnWait extends ActionOnWait {

    @Override
    public void doOnWait() {
        Thread.yield();
    }

}
