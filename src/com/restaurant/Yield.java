package com.restaurant;

public final class Yield extends ActionOnWait {

    @Override
    public void actOnWait() {
        Thread.yield();
    }

}
