import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Node {
    String hostname;
    int port;
    int nodeNumber;
    int interRequestDelay;
    int csExecutionTime;
    int numRequests;
    int[] qMembers;
    ServerSocket serverSocket;
    Connection[] clientSockets;

    private boolean listen() {
        int numConnected = 0;
        try {
            this.serverSocket = new ServerSocket(this.port);
            long start = System.currentTimeMillis();
            while (numConnected < clientSockets.length) {
                Socket client = serverSocket.accept(); 
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                clientSockets[Integer.parseInt(in.readLine())] = new Connection(client, in, out); //clientSockets[node_number], connecting client must send their node number once accepted

                if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                    for (int i = 0; i < clientSockets.length; i++) {
                        clientSockets[i].close();
                    }
                    return false;
                }
                numConnected++;
            }
        }
        catch(IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean connect() {
        
    }

    public void establishConnections() {
        listen();
        connect();
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
