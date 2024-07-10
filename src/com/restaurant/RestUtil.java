package com.restaurant;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RestUtil {

    public static final Set<Integer> SEATS = Set.of(2, 3, 4, 5, 6);
    public static final Set<Integer> CLIENT_GROUPS = Set.of(1, 2, 3, 4, 5, 6);

    public static Map<ClientsGroup, Table> clientsGroupTableMap(AtomicReferenceArray<RestManager.RestaurantManager.SeatData> seats) {
        record ClientGroupToTable(ClientsGroup clientsGroup, Table table) {
        }
        return IntStream.range(0, seats.length())
                .mapToObj(seats::get)
                .<ClientGroupToTable>mapMulti((seatData, consumer) -> { // requires Java 16
                    for (ClientsGroup cgs : seatData.clientsGroups()) {
                        consumer.accept(new ClientGroupToTable(cgs, seatData.table()));
                    }
                })
                .collect(Collectors.toMap(ClientGroupToTable::clientsGroup, ClientGroupToTable::table, (t1, t2) -> t1, HashMap::new));
    }

}

