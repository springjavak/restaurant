package com.restaurant;

public final class SpinOnWait extends ActionOnWait {

    @Override
    public void actOnWait() {
        Thread.onSpinWait();
    }

}
