package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
    private final Endpoint endpoint;

    public ClientCommunicator() {
        endpoint = new Endpoint();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;

        private ClientForwarder() {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        public void handOff(FishModel fish, InetSocketAddress receiver) {
            endpoint.send(receiver, new HandoffRequest(fish));
        }

        public void handOffToken(InetSocketAddress receiver) {
            endpoint.send(receiver, new Token());
        }

        public void sendSnapshotMarker(InetSocketAddress... receivers) {
            for (InetSocketAddress receiver : receivers) {
                endpoint.send(receiver, new SnapshotMarker());
            }
        }

        public void sendSnapshotResult(Set<FishModel> snapshot, InetSocketAddress receiver) {
            endpoint.send(receiver, new SnapshotResult(new HashSet<>(snapshot)));
        }

        public void sendLocationRequest(String fishId, InetSocketAddress receiver) {
            endpoint.send(receiver, new LocationRequest(fishId));
        }
    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();

                if (msg.getPayload() instanceof RegisterResponse)
                    tankModel.onRegistration(((RegisterResponse) msg.getPayload()).id());

                if (msg.getPayload() instanceof HandoffRequest)
                    tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

                if (msg.getPayload() instanceof NeighborUpdate)
                    tankModel.updateNeighbors(((NeighborUpdate) msg.getPayload()).getLeftNeighbor(),
                            ((NeighborUpdate) msg.getPayload()).getRightNeighbor());
                if (msg.getPayload() instanceof Token)
                    tankModel.receiveToken();

                if (msg.getPayload() instanceof SnapshotMarker)
                    tankModel.receiveSnapshotMarker(msg.getSender());

                if (msg.getPayload() instanceof SnapshotResult)
                    tankModel.receiveSnapshotResult(((SnapshotResult) msg.getPayload()).snapshotResult());

                if (msg.getPayload() instanceof LocationRequest)
                    tankModel.locateFishGlobally(((LocationRequest) msg.getPayload()).fishId());
            }
            System.out.println("Receiver stopped.");
        }
    }

    public ClientForwarder newClientForwarder() {
        return new ClientForwarder();
    }

    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }

}
