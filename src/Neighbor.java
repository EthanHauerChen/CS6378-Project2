import java.util.Arrays;

public class Neighbor {
    int nodeNumber;
    String hostname;
    int port;

    public Neighbor(int n, String h, int p) {
        nodeNumber = n;
        hostname = h;
        port = p;
    }

    private String lt() { return "\n\t"; }
    @Override
    public String toString() {
        return "" + nodeNumber;
    }
}
