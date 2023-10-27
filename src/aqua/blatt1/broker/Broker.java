package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt2.PoisonPill;
import aqua.blatt2.Poisoner;
import messaging.Endpoint;
import messaging.Message;

import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    private final Endpoint endpoint = new Endpoint(4711);
    private final ClientCollection<InetSocketAddress> clients = new ClientCollection<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean stopRequested = false;


    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.run();
    }

    public void run() {
        System.out.println("Broker started");

        var executor = Executors.newFixedThreadPool(10);

        new Thread(() -> Poisoner.main(null)).start();
//        new Thread(() -> {
//            final int confirmed = JOptionPane.showConfirmDialog(
//                    null,
//                    "Close broker?",
//                    "Broker",
//                    JOptionPane.OK_CANCEL_OPTION
//            );
//            if (confirmed == JOptionPane.OK_OPTION)
//                stopRequested = true;
//        }).start();

        while (!stopRequested) {
            final Message message = endpoint.blockingReceive();
            if (message != null)
                executor.execute(new Thread(new BrokerTask(message)));
        }
        System.out.println("Broker stopped");
        executor.shutdown();
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
                deregister(((DeregisterRequest) message.getPayload()).getId());
            } else if (message.getPayload() instanceof HandoffRequest) {
                handoffFish(message.getSender(), (HandoffRequest) message.getPayload());
            } else if (message.getPayload() instanceof PoisonPill) {
                stopRequested = true;
            } else {
                System.err.println("Unknown message type");
            }
        }

        private void register(InetSocketAddress client) {
            lock.readLock().lock();
            final String name = "Tank" + (clients.size() + 1);
            lock.readLock().unlock();

            lock.writeLock().lock();
            clients.add(name, client);
            lock.writeLock().unlock();
            endpoint.send(client, new RegisterResponse(name));
        }

        private void deregister(String name) {
            lock.readLock().lock();
            final int index = clients.indexOf(name);
            lock.readLock().unlock();

            if (index == -1) {
                System.err.println("No Client registered under that name");
                return;
            }

            lock.writeLock().lock();
            clients.remove(index);
            lock.writeLock().unlock();
        }

        private void handoffFish(InetSocketAddress client, HandoffRequest request) {
            lock.readLock().lock();
            final int index = clients.indexOf(client);
            if (request.getFish().getDirection() == Direction.LEFT) {
                endpoint.send(clients.getLeftNeighborOf(index), request);
            } else {
                endpoint.send(clients.getRightNeighborOf(index), request);
            }
            lock.readLock().unlock();
        }
    }
}