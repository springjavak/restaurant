package com.restaurant.wait;

public final class YieldOnWait extends ActionOnWait {

    @Override
    public void doOnWait() {
        Thread.yield();
    }

}
