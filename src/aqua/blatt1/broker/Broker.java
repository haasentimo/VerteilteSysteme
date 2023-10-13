package aqua.blatt1.broker;

import messaging.Endpoint;

import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;

import java.net.InetSocketAddress
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

            switch (MsgType.valueOf(m.getPayload())){
                case REGISTER -> register(m.getSender());
                case DEREGISTER -> deregister(m.getSender());
                case HANDOFF -> handoffFish(m.getSender());
                default -> System.err.println("Unknown message");
            }
        }
    }

    private void register(InetSocketAddress client){
        final String name = "Tank" + (clients.size() + 1);
        clients.add(name, client);
        endpoint.send(client, new RegiserResponse(name))
    }

    private void deregister(String name){
        final int index = clients.indexOf(name);
        if (index == -1){
            System.err.println("No Client registered under that name");
            return;
        }
        clients.remove(index)
    }

    private void handoffFish(){

    }
}