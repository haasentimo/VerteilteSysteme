package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import messaging.Endpoint;
import messaging.Message;

import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;

import java.net.InetSocketAddress;
public class Broker {
    private final Endpoint endpoint = new Endpoint(4711);
    private final ClientCollection<InetSocketAddress > clients = new ClientCollection<>();


    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.run();
    }

    private void run() {
        while(true) {
            Message m = endpoint.blockingReceive();

            if (m.getPayload() instanceof RegisterRequest){
                register(m.getSender());
            } else if (m.getPayload() instanceof DeregisterRequest){
                deregister(((DeregisterRequest) m.getPayload()).getId());
            } else if (m.getPayload() instanceof HandoffRequest){
                handoffFish(m.getSender(), (HandoffRequest) m.getPayload());
            } else {
                System.err.println("Unknown message type");
            }
        }
    }

    private void register(InetSocketAddress client){
        final String name = "Tank" + (clients.size() + 1);
        clients.add(name, client);
        endpoint.send(client, new RegisterResponse(name));
    }

    private void deregister(String name){
        final int index = clients.indexOf(name);
        if (index == -1){
            System.err.println("No Client registered under that name");
            return;
        }
        clients.remove(index);
    }

    private void handoffFish(InetSocketAddress client, HandoffRequest request){
        final int index = clients.indexOf(client);
        if (request.getFish().getDirection() == Direction.LEFT){
            endpoint.send(clients.getLeftNeighborOf(index), request);
        } else {
            endpoint.send(clients.getRightNeighborOf(index), request);
        }

    }
}