package com.restaurant.wait;

public final class SpinOnWait extends ActionOnWait {

    @Override
    public void doOnWait() {
        Thread.onSpinWait();
    }

}
