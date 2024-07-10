package com.restaurant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.IntStream;

public class RestManager implements Manager {

    /**
     * Bridge pattern used here
     */
    private final Manager restaurantManager;

    public RestManager(List<Table> tables, boolean lockFree, ActionOnWait actionOnWait) {
        restaurantManager = lockFree ? new LockFreeRestaurantManager(tables, actionOnWait) : new BlockingRestaurantManager(tables, actionOnWait);
    }

    @Override
    public int getQueueCount() {
        return this.restaurantManager.getQueueCount();
    }

    @Override
    public int getSeatCount() {
        return this.restaurantManager.getSeatCount();
    }

    @Override
    public void onArrive(ClientsGroup group) {
        this.restaurantManager.onArrive(group);
    }

    @Override
    public void onLeave(ClientsGroup group) {
        this.restaurantManager.onLeave(group);
    }

    @Override
    public Table lookup(ClientsGroup group) {
        return this.restaurantManager.lookup(group);
    }

    @Override
    public boolean abandonQueue(ClientsGroup group) {
        return this.restaurantManager.abandonQueue(group);
    }

    @Override
    public boolean abandonQueueIf(ClientsGroup group, Duration waitLimit) {
        return this.restaurantManager.abandonQueueIf(group, waitLimit);
    }

    @Override
    public boolean abandonAllIf(Duration waitLimit) {
        return this.restaurantManager.abandonAllIf(waitLimit);
    }

    abstract static class RestaurantManager implements Manager {
        protected final Queue<ClientsGroup> clientGroupQueue = new ConcurrentLinkedQueue<>();
        /**
         * Strategy pattern used here.
         *
         * <p>Thread::onSpinWait (magical trick since Java 9) can be freely used here as a strategy, since it is suggested by JDK documentation and supported by latest Intel chips at least
         * <p>Thread::yield can be the second option. It will free the CPU for other threads, but it is not guaranteed that the current thread will be scheduled again immediately
         * Yielding will favor application's global progress over the concrete thread's local progress
         * <p>Runnable can also be null, since it is used in a null-safe way
         */
        protected final ActionOnWait actionOnWait;

        public RestaurantManager(ActionOnWait actionOnWait) {
            this.actionOnWait = actionOnWait;
        }

        @Override
        public int getQueueCount() {
            return this.clientGroupQueue.size();
        }

        protected abstract Optional<?> findFreeTable(ClientsGroup group);

        protected abstract Optional<?> findNonFreeTable(ClientsGroup group);

        protected abstract boolean tryTakeSeat(ClientsGroup group);

        protected record SeatData(Table table, List<ClientsGroup> clientsGroups, int seatsLeft) {
        }
    }


    static final class LockFreeRestaurantManager extends RestaurantManager {

        private final AtomicReferenceArray<SeatData> seats;
        private final AtomicMarkableReference<Runnable> loopingTask;

        public LockFreeRestaurantManager(List<Table> tables, ActionOnWait actionOnWait) {
            super(actionOnWait);
            this.seats = new AtomicReferenceArray<>(tables.size());
            for (int i = 0; i < tables.size(); i++) {
                this.seats.set(i, new SeatData(tables.get(i), new LinkedList<>(), tables.get(i).size()));
            }
            this.loopingTask = new AtomicMarkableReference<>(null, false);
        }

        @Override
        public int getSeatCount() {
            return IntStream.range(0, this.seats.length()).map(i -> this.seats.get(i).table().size()-this.seats.get(i).seatsLeft).sum();
        }

        private void conditionallyInitializeEventLoop() {
            var r = this.loopingTask.getReference();
            if (this.loopingTask.compareAndSet(null, this::loopOverClientGroupQueue, false, true)) {
                CompletableFuture.runAsync(this.loopingTask.getReference()); // runs once and forever
            }
        }

        @Override
        public void onArrive(ClientsGroup group) {
            this.conditionallyInitializeEventLoop();
            boolean waitingQueueContainsSimilarCandidate = this.clientGroupQueue.stream().anyMatch(cg -> cg.size() <= group.size());
            if (waitingQueueContainsSimilarCandidate) {
                this.clientGroupQueue.offer(group);
            } else if (!tryTakeSeat(group)) {
                this.clientGroupQueue.offer(group);
            }
        }

        @Override
        public void onLeave(ClientsGroup group) {
            var freedUpSize = group.size();
            IndexedSeatData indexedSeatData;
            SeatData newSeatData;
            List<ClientsGroup> newClientGroups;
            do {
                indexedSeatData = this.loadSeatData(group);
                newClientGroups = new LinkedList<>(indexedSeatData.seatData().clientsGroups());
                newClientGroups.remove(group);
                newSeatData = new SeatData(
                        indexedSeatData.seatData().table(),
                        newClientGroups,
                        indexedSeatData.seatData().seatsLeft() + freedUpSize);
            } while (!this.updateSeatData(indexedSeatData, newSeatData));

        }

        private IndexedSeatData loadSeatData(ClientsGroup group) {
            return IntStream.range(0, this.seats.length())
                    .mapToObj(i -> new IndexedSeatData(i, this.seats.get(i)))
                    .filter(sd -> sd.seatData().clientsGroups().contains(group))
                    .findAny()
                    .orElseThrow(IllegalStateException::new);
        }

        @Override
        public Table lookup(ClientsGroup group) {
            return RestUtil.clientsGroupTableMap(this.seats).get(group);
        }

        @Override
        public boolean abandonQueue(ClientsGroup group) {
            return this.clientGroupQueue.remove(group);
        }

        @Override
        public boolean abandonQueueIf(ClientsGroup group, Duration waitLimit) {
            return this.clientGroupQueue.removeIf(cg -> cg.equals(group) && Duration.between(cg.arrivalTime(), LocalDateTime.now()).compareTo(waitLimit) > 0);
        }

        @Override
        public boolean abandonAllIf(Duration waitLimit) {
            return this.clientGroupQueue.removeIf(cg -> Duration.between(cg.arrivalTime(), LocalDateTime.now()).compareTo(waitLimit) > 0);
        }

        private void loopOverClientGroupQueue() {
            while (true) {
                while (this.clientGroupQueue.isEmpty()) {
                    Optional.ofNullable(this.actionOnWait).ifPresent(ActionOnWait::actOnWait);
                }
                this.clientGroupQueue.removeIf(this::tryTakeSeat);
            }
        }

        @Override
        protected boolean tryTakeSeat(ClientsGroup group) {
            while (this.findFreeTable(group).or(() -> this.findNonFreeTable(group)).isPresent()) { // no reference for having fresh data
                Optional<IndexedSeatData> optionalIndexedSeatData = this.findFreeTable(group)
                        .or(() -> this.findNonFreeTable(group)); // getting again for fresh data
                if (optionalIndexedSeatData.isPresent()) { // checking whether the table is still available
                    var indexedOldSeatData = optionalIndexedSeatData.get();
                    var newClientGroups = indexedOldSeatData.seatData().clientsGroups();
                    newClientGroups.add(group);
                    var newSeatData = new SeatData(
                            indexedOldSeatData.seatData().table(),
                            newClientGroups,
                            indexedOldSeatData.seatData().seatsLeft() - group.size());
                    if (this.updateSeatData(optionalIndexedSeatData.get(), newSeatData)) { // last point to check whether the table is still available
//                    clientGroupQueue.remove(group);
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * This operation is lock-free, atomic and visible.
         * <p>Those guarantees are supported on hardware level.
         * <p>The protocol is called CAS (compare and swap) on Intel chips and Store-Conditional on ARM chips.
         */
        private boolean updateSeatData(IndexedSeatData indexedSeatData, SeatData seatData) {
            return this.seats.compareAndSet(indexedSeatData.index(), indexedSeatData.seatData(), seatData);
        }

        @Override
        protected Optional<IndexedSeatData> findFreeTable(ClientsGroup group) {
            return IntStream.range(0, this.seats.length())
                    .mapToObj(i -> new IndexedSeatData(i, this.seats.get(i)))
                    .sorted(Comparator.comparingInt(sd -> sd.seatData().table().size()))
                    .filter(sd -> sd.seatData().table().size() == sd.seatData().seatsLeft())
                    .filter(sd -> sd.seatData().seatsLeft() >= group.size())
                    .findFirst();
        }

        @Override
        protected Optional<IndexedSeatData> findNonFreeTable(ClientsGroup group) {
            return IntStream.range(0, this.seats.length())
                    .mapToObj(i -> new IndexedSeatData(i, seats.get(i)))
                    .sorted(Comparator.comparingInt(sd -> sd.seatData().table().size()))
                    .filter(sd -> sd.seatData().clientsGroups().size() > 0)
                    .filter(sd -> 0 < sd.seatData().seatsLeft())
                    .filter(sd -> sd.seatData().seatsLeft() >= group.size())
                    .findFirst();
        }

        record IndexedSeatData(int index, SeatData seatData) {
        }


    }

    static final class BlockingRestaurantManager extends RestaurantManager {
        public BlockingRestaurantManager(List<Table> tables, ActionOnWait actionOnBusyWait) {
            super(actionOnBusyWait);
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public void onArrive(ClientsGroup group) {

        }

        @Override
        public void onLeave(ClientsGroup group) {

        }

        @Override
        public Table lookup(ClientsGroup group) {
            return null;
        }

        @Override
        public boolean abandonQueue(ClientsGroup group) {
            return false;
        }

        @Override
        public boolean abandonQueueIf(ClientsGroup group, Duration waitLimit) {
            return false;
        }

        @Override
        public boolean abandonAllIf(Duration waitLimit) {
            return false;
        }

        @Override
        protected Optional<?> findFreeTable(ClientsGroup group) {
            return null;
        }

        @Override
        protected Optional<?> findNonFreeTable(ClientsGroup group) {
            return null;
        }

        @Override
        protected boolean tryTakeSeat(ClientsGroup group) {
            return false;
        }
    }

}
