package com.restaurant;

public final class SpinOnWait extends ActionOnWait {

    @Override
    public void doOnWait() {
        Thread.onSpinWait();
    }

}
