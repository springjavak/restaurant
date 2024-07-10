package com.restaurant;

public final class Spin extends ActionOnWait {

    @Override
    public void actOnWait() {
        Thread.onSpinWait();
    }

}
