package com.restaurant;

import java.time.LocalDateTime;

public record ClientsGroup(int size, LocalDateTime arrivalTime) {

    public static ClientsGroup ofCurrentTime(int size) {
        return new ClientsGroup(size, LocalDateTime.now());
    }

    public ClientsGroup(int size, LocalDateTime arrivalTime) {
        if (!RestUtil.CLIENT_GROUPS.contains(size)) {
            throw new ClientNumberException("Invalid number of clients");
        }
        this.size = size;
        this.arrivalTime = arrivalTime;
    }
}
