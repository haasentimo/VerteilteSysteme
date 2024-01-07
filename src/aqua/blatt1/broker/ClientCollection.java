package aqua.blatt1.broker;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or
 * externally synchronized.
 */

public class ClientCollection<T> {
    private class Client {
        String id;
        final T client;
        long timestamp;

        Client(String id, T client) {
            this.id = id;
            this.client = client;
            this.timestamp = System.currentTimeMillis();
        }

        void updateTimestamp() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final List<Client> clients;

    public ClientCollection() {
        clients = new ArrayList<Client>();
    }

    public ClientCollection<T> add(String id, T client) {
        clients.add(new Client(id, client));
        return this;
    }

    public ClientCollection<T> remove(int index) {
        clients.remove(index);
        return this;
    }

    public int indexOf(String id) {
        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).id.equals(id))
                return i;
        return -1;
    }

    public int indexOf(T client) {
        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).client.equals(client))
                return i;
        return -1;
    }

    public T getClient(int index) {
        return clients.get(index).client;
    }

    public int size() {
        return clients.size();
    }

    public T getLeftNeighborOf(int index) {
        return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
    }

    public T getRightNeighborOf(int index) {
        return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
    }

    public void updateTimestamp(int index, String newId) {
        var entry = clients.get(index);
        entry.id = newId;
        entry.updateTimestamp();
    }

    public List<String> collectToClean(long timestamp) {
        return clients.stream()
                .filter(client -> client.timestamp < timestamp)
                .collect(LinkedList::new, (list, client) -> list.add(client.id), LinkedList::addAll);
    }

}
