import java.util.Arrays;

public class Node {
    String hostname;
    int port;
    int nodeNumber;
    int interRequestDelay;
    int csExecutionTime;
    int numRequests;
    int[] qMembers;

    public void establishConnections() {

    }
    
    public void beginProtocol() {

    }

    private String lt() { return "\n\t"; }

    @Override
    public String toString() {
        return "Node {\n\t" +
        "hostname: " + hostname + lt() +
        "port: " + port + lt() + 
        "nodeNumber: " + nodeNumber  + lt() +
        "interRequestDelay: " + interRequestDelay + lt() +
        "csExecutionTime: " + csExecutionTime + lt() +
        "numRequests: " + numRequests + lt() +
        "quorum members: " + Arrays.toString(qMembers) + 
        "\n}";
    }
}
