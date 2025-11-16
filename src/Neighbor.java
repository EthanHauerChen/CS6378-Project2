import java.util.Arrays;

public class Neighbor {
    int nodeNumber;
    String hostname;
    int port;
    Connection connection;

    public Neighbor(int n, String h, int p) {
        nodeNumber = n;
        hostname = h;
        port = p;
    }

    public void addConnection(Connection c) {
        if (connection != null) {
            System.out.println("Error, connection already exists");
            return;
        }
        connection = c;
    }

    private String lt() { return "\n\t"; }
    @Override
    public String toString() {
        return "(node: " + nodeNumber + ", hostname: " + hostname + ", port: " + port + ")";
    }
}
