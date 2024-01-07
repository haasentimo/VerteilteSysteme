package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.NameResolutionResponse;

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;
    protected InetSocketAddress leftNeighbor;
    protected InetSocketAddress rightNeighbor;
    protected boolean hasToken = false;
    protected Timer timer = new Timer();
    protected boolean hasSnapshotToken = false;
    HashSet<FishModel> globalSnapshot;
    private Set<FishModel> localSnapshot;
    private SnapshotStates snapshotRecordingState = SnapshotStates.IDLE;
    private boolean initiatedSnapshot = false;
    protected final Map<String, InetSocketAddress> homeAgent = new HashMap<>();

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
    }

    synchronized void onRegistration(String id, int leaseDuration) {
        this.id = id;
        if (fishCounter == 0)
            newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                forwarder.register();
            }
        }, leaseDuration / 2);
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            String fishId = "fish" + (++fishCounter) + "@" + getId();
            FishModel fish = new FishModel(fishId, x, y, rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
            homeAgent.put(fishId, null);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        switch (fish.getDirection()) {
            case LEFT -> addToSnapshotIfState(SnapshotStates.RIGHT, fish);
            case RIGHT -> addToSnapshotIfState(SnapshotStates.LEFT, fish);
        }
        fish.setToStart();
        fishies.add(fish);
        if (fish.getTankId().equals(getId()))
            homeAgent.put(fish.getId(), null);
        else
            forwarder.sendNameResolutionRequest(fish.getTankId(), fish.getId());
    }

    private void addToSnapshotIfState(SnapshotStates onState, FishModel fish) {
        if (snapshotRecordingState == onState || snapshotRecordingState == SnapshotStates.BOTH)
            localSnapshot.add(fish);
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge())
                if (hasToken())
                    if (fish.getDirection() == Direction.LEFT) {
                        forwarder.handOff(fish, leftNeighbor);
                    } else {
                        forwarder.handOff(fish, rightNeighbor);
                    }
                else
                    fish.reverse();

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id, hasToken);
    }

    public synchronized void updateNeighbors(InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
        if (leftNeighbor != null)
            this.leftNeighbor = leftNeighbor;
        if (rightNeighbor != null)
            this.rightNeighbor = rightNeighbor;
    }

    public boolean hasToken() {
        return hasToken;
    }

    public synchronized void receiveToken() {
        final int DELAY = 2000;

        if (!hasToken) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    hasToken = false;
                    forwarder.handOffToken(rightNeighbor);
                }
            }, DELAY);
        }
        this.hasToken = true;
    }

    public void initiateSnapshot() {
        if (snapshotRecordingState != SnapshotStates.IDLE)
            return;
        localSnapshot = getNonDepartingFishies();
        snapshotRecordingState = SnapshotStates.BOTH;
        forwarder.sendSnapshotMarker(leftNeighbor, rightNeighbor);
        hasSnapshotToken = true;
        initiatedSnapshot = true;
    }

    private Set<FishModel> getNonDepartingFishies() {
        Set<FishModel> nonDepartingFishies = new HashSet<FishModel>();
        for (FishModel fish : fishies) {
            if (!fish.isDeparting())
                nonDepartingFishies.add(fish);
        }
        return nonDepartingFishies;
    }

    public void receiveSnapshotMarker(InetSocketAddress sender) {
        final var oldState = snapshotRecordingState;
        switch (snapshotRecordingState) {
            case IDLE -> {
                localSnapshot = getNonDepartingFishies();
                snapshotRecordingState = sender.equals(leftNeighbor) ? SnapshotStates.RIGHT : SnapshotStates.LEFT;
                forwarder.sendSnapshotMarker(leftNeighbor, rightNeighbor);
            }
            case LEFT -> sendResultOnFinalMarker(sender, leftNeighbor);
            case RIGHT -> sendResultOnFinalMarker(sender, rightNeighbor);
            case BOTH ->
                    snapshotRecordingState = sender.equals(leftNeighbor) ? SnapshotStates.RIGHT : SnapshotStates.LEFT;
        }
        System.out.println("Received snapshot in state " + oldState + " now in state " + snapshotRecordingState);
    }

    private void sendResultOnFinalMarker(InetSocketAddress sender, InetSocketAddress neighbor) {
        if (sender.equals(neighbor)) {
            snapshotRecordingState = SnapshotStates.IDLE;
            if (hasSnapshotToken) {
                forwarder.sendSnapshotResult(localSnapshot, leftNeighbor);
                hasSnapshotToken = false;
                System.out.println("Sent snapshot result " + localSnapshot.toString());
            } else System.out.println("Local Snapshot done but no snapshot token");
        }
    }

    public void receiveSnapshotResult(HashSet<FishModel> snapshotResult) {
        System.out.println("Received snapshot result " + snapshotResult.toString());
        if (initiatedSnapshot) {
            globalSnapshot = snapshotResult;
            initiatedSnapshot = false;
            System.out.println("Global snapshot: " + globalSnapshot);
        } else {
            snapshotResult.addAll(localSnapshot);
            if (snapshotRecordingState == SnapshotStates.IDLE) {
                System.out.println("Forwarding snapshot result " + snapshotResult);
                forwarder.sendSnapshotResult(snapshotResult, leftNeighbor);
            } else hasSnapshotToken = true;
        }
    }

    public void locateFishGlobally(String fishId) {
        final InetSocketAddress tankAddress = homeAgent.get(fishId);
        if (tankAddress == null) {
            locateFishLocally(fishId);
        } else {
            forwarder.sendLocationRequest(fishId, tankAddress);
        }
    }

    public void receiveNameResolutionResponse(NameResolutionResponse response) {
        forwarder.sendLocationUpdate(response.address(), response.reqId());
    }

    public void receiveLocationUpdate(InetSocketAddress sender, String reqId) {
        homeAgent.put(reqId, sender);
    }

    public void locateFishLocally(String fishId) {
        fishies.stream()
                .filter(fish -> fish.getId()
                .equals(fishId)).findFirst()
                .ifPresent(FishModel::toggle);
    }

    private enum SnapshotStates {
        IDLE, LEFT, RIGHT, BOTH
    }

}