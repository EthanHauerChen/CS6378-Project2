import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    Neighbor[] qMembers;
    Connection[] clientSockets;

    /**
     * 
     * @return returns 0 upon success, returns -1 if it failed to bind to a neighbor, returns 1 if it failed to accept() that neighbor, returns 2 if timeout
     */
    public int establishConnections() {
        try {
            long start = System.currentTimeMillis();
            for (int i = 0; i < qMembers.length; i++) { //bind to neighbors with larger IDs
                if (this.qMembers[i].nodeNumber > this.nodeNumber) { //if this.nodeNumber < neighbor, bind socket
                    Socket client = new Socket(this.qMembers[i].hostname, this.qMembers[i].port);
                    ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    clientSockets[i] = new Connection(client, in, out); //clientSockets[node_number]
                    clientSockets[i].writeInt(this.nodeNumber); //once connected, send node_number as initial message
                }

                if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                    for (int j = 0; j < clientSockets.length; j++) {
                        clientSockets[j].close();
                    }
                    return 2;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < qMembers.length; i++) { //bind to neighbors with larger IDs, doesn't actually bind to node i, but will guarantee that it calls accept() the correct number of times
                if (this.qMembers[i].nodeNumber < this.nodeNumber) { //if this.nodeNumber > neighbor, accept socket
                    Socket client = serverSocket.accept();
                    ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    clientSockets[in.readInt()] = new Connection(client, in, out); //clientSockets[node_number], connecting client must send their node number once accepted
                }

                if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                    for (int j = 0; j < clientSockets.length; j++) {
                        clientSockets[j].close();
                    }
                    return 2;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return 1;
        }
        
        return 0; //success
    }
    
    public void beginProtocol() {
        switch(establishConnections()) {
            case 0:
                //start protocol, TODO
                break;
            case -1:
                //print error reason
                break;
            case 1:
                //print error reason
                break;
            case 2:
                //print error reason
        }
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
