package aqua.blatt1.common.msgtypes;

import java.net.InetSocketAddress;

public final class NeighborUpdate implements java.io.Serializable{
    private final InetSocketAddress leftNeighbor;
    private final InetSocketAddress rightNeighbor;

    public NeighborUpdate(InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
        this.leftNeighbor = leftNeighbor;
        this.rightNeighbor = rightNeighbor;
    }

    public InetSocketAddress getLeftNeighbor() {
        return leftNeighbor;
    }

    public InetSocketAddress getRightNeighbor() {
        return rightNeighbor;
    }
}
