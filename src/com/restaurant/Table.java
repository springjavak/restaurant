package com.restaurant;

public record Table(int size) {

    public Table(int size) {
        if (!RestUtil.SEATS.contains(size)) {
            throw new SeatNumberException("Invalid number of seats");
        }
        this.size = size;
    }

}
