package aqua.blatt1.broker;

import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.PoisonPill;
import aqua.blatt2.Poisoner;
import messaging.Endpoint;
import messaging.Message;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    private final Endpoint endpoint = new Endpoint(4711);
    private final ClientCollection<InetSocketAddress> clients = new ClientCollection<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, InetSocketAddress> nameResolutionTable = new HashMap<>();
    private volatile boolean stopRequested = false;
    final int leaseDuration = 10000;
    private final Timer timer = new Timer();


    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.run();
    }

    public void run() {
        System.out.println("Broker started");

        var executor = Executors.newFixedThreadPool(10);

        final boolean[] addCleanupTask = {true};

        new Thread(() -> Poisoner.main(null)).start();

        while (!stopRequested) {
            final Message message = endpoint.blockingReceive();
            if (message != null)
                executor.execute(new Thread(new BrokerTask(message)));
            if (addCleanupTask[0]) {
                executor.execute(new CleanupTask(executor));
                addCleanupTask[0] = false;
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        addCleanupTask[0] = true;
                    }
                }, leaseDuration * 2);
            }
        }
        System.out.println("Broker stopped");
        executor.shutdown();
    }

    public class CleanupTask implements Runnable {

        ExecutorService executor;

        public CleanupTask(ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void run() {
            cleanup();
        }

        private void cleanup() {
            lock.readLock().lock();
            List<String> toClean = clients.collectToClean(System.currentTimeMillis() - leaseDuration);
            lock.readLock().unlock();
            toClean.forEach(id -> executor.execute(
                    new BrokerTask(
                            new Message(new DeregisterRequest(id, false), nameResolutionTable.get(id)
                            )
                    )));
        }
    }

    private class BrokerTask implements Runnable {
        private final Message message;

        public BrokerTask(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            if (message.getPayload() instanceof RegisterRequest) {
                register(message.getSender());
            } else if (message.getPayload() instanceof DeregisterRequest) {
                deregister(((DeregisterRequest) message.getPayload()).id());
            } else if (message.getPayload() instanceof PoisonPill) {
                stopRequested = true;
            } else if (message.getPayload() instanceof NameResolutionRequest) {
                final NameResolutionRequest request = (NameResolutionRequest) message.getPayload();
                endpoint.send(
                        message.getSender(),
                        new NameResolutionResponse(nameResolutionTable.get(request.tankId()), request.reqId())
                );
            } else {
                System.err.println("Unknown message type");
            }
        }

        private void register(InetSocketAddress client) {
            lock.readLock().lock();
            final int index = clients.indexOf(client) != -1 ? clients.indexOf(client) + 1 : clients.size() + 1;
            lock.readLock().unlock();
            final String name = "Tank" + index;

            lock.writeLock().lock();
            int ind = clients.indexOf(name);
            if (ind != -1)
                clients.updateTimestamp(ind, name);
            else
                clients.add(name, client);
            lock.writeLock().unlock();

            lock.readLock().lock();
            final InetSocketAddress leftNeighbor = clients.getLeftNeighborOf(clients.indexOf(name));
            final InetSocketAddress rightNeighbor = clients.getRightNeighborOf(clients.indexOf(name));
            lock.readLock().unlock();

            endpoint.send(client, new RegisterResponse(name, new NeighborUpdate(leftNeighbor, rightNeighbor), leaseDuration));
            nameResolutionTable.put(name, client);
            endpoint.send(client, new NeighborUpdate(leftNeighbor, rightNeighbor));
            endpoint.send(leftNeighbor, new NeighborUpdate(null, client));
            endpoint.send(rightNeighbor, new NeighborUpdate(client, null));


            if (index == 1) {
                endpoint.send(client, new Token());
            }
        }

        private void deregister(String name) {
            lock.readLock().lock();
            final int index = clients.indexOf(name);
            lock.readLock().unlock();

            if (index == -1) {
                System.err.println("No Client registered under that name");
                return;
            }

            lock.readLock().lock();
            final InetSocketAddress leftNeighbor = clients.getLeftNeighborOf(clients.indexOf(name));
            final InetSocketAddress rightNeighbor = clients.getRightNeighborOf(clients.indexOf(name));
            lock.readLock().unlock();

            lock.writeLock().lock();
            clients.remove(index);
            lock.writeLock().unlock();

            endpoint.send(leftNeighbor, new NeighborUpdate(null, rightNeighbor));
            endpoint.send(rightNeighbor, new NeighborUpdate(leftNeighbor, null));
        }
    }
}