import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    private void listen() {
        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < qMembers.length; i++) { //bind to neighbors with larger IDs, doesn't actually bind to node i, but will guarantee that it calls accept() the correct number of times
                if (this.qMembers[i].nodeNumber < this.nodeNumber) { //if this.nodeNumber > neighbor, accept socket
                    Socket client = serverSocket.accept();
                    ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    int nodenum = in.readInt();
                    clientSockets[nodenum] = new Connection(client, in, out); //clientSockets[node_number], connecting client must send their node number once accepted
                    System.out.println("Node " + this.nodeNumber + " read " + nodenum + " from " + nodenum);
                }

                if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                    for (int j = 0; j < clientSockets.length; j++) {
                        clientSockets[j].close();
                    }
                    System.out.println("Node " + this.nodeNumber + ": Timeout during listening phase");
                    return;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException during listen phase");
            return;
        }
        
        System.out.println("Node " + this.nodeNumber + ": listening socket successfully accepted all clients");
    }

    private void bind() {
        try {
            long start = System.currentTimeMillis();
            for (int i = 0; i < qMembers.length; i++) { //bind to neighbors with larger IDs
                if (this.qMembers[i].nodeNumber > this.nodeNumber) { //if this.nodeNumber < neighbor, bind socket
                    Socket client = new Socket(this.qMembers[i].hostname, this.qMembers[i].port);
                    ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    clientSockets[i] = new Connection(client, in, out); //clientSockets[node_number]
                    clientSockets[i].writeInt(this.nodeNumber); //once connected, send node_number as initial message
                    System.out.println("Node " + this.nodeNumber + "wrote " + this.nodeNumber + " to " + i);
                }

                if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                    for (int j = 0; j < clientSockets.length; j++) {
                        clientSockets[j].close();
                    }
                    System.out.println("Node " + this.nodeNumber + ": Timeout during binding phase");
                    return;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException during listen phase");
            return;
        }

        System.out.println("Node " + this.nodeNumber + ": listening socket successfully accepted all clients");
    }

    /**
     * @return returns true upon success, false upon failure
     */
    public boolean establishConnections() {
        Thread l = new Thread(() -> listen());
        Thread b = new Thread(() -> bind());
        //create listening sockets
        l.start();
        // try { //wait some amount of time before having clients attempt to connect
        //     Thread.sleep(5000); 
        // }
        // catch (InterruptedException e) {
        //     //do nothing, sleep was interrupted which isn't big deal
        // }

        //bind to the listening sockets of other nodes
        b.start();

        try {
            l.join();
            b.join();
        }
        catch (InterruptedException e) {
            return false;
        }

        return true;
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
