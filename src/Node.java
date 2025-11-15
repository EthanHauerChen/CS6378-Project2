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

    public Node(String hostname, int port, int nodenum, int interRequestDelay, int csExecutionTime, int numRequests, Neighbor[] qMembers) {
        this.hostname = hostname;
        this.port = port;
        this.nodeNumber = nodenum;
        this.interRequestDelay = interRequestDelay;
        this.csExecutionTime = csExecutionTime;
        this.numRequests = numRequests;
        this.qMembers = qMembers;
        clientSockets = new Connection[this.qMembers.length];
    }

    private void closeConnections() {
        for (int i = 0; i < clientSockets.length; i++) {
            try {
                if (clientSockets[i] != null)
                    clientSockets[i].close();
            }
            catch(IOException e) {
                //do nothing, failure to close is no big deal
            }
        }
    }

    private int numNeighborsSmaller() { //returns number of neighors with smaller node number than this
        int numSmaller = 0;
        for (Neighbor n : this.qMembers) {
            if (n.nodeNumber < this.nodeNumber) numSmaller++;
        }
        return numSmaller;
    }
    private int numNeighborsLarger() { //see above
        return this.qMembers.length - numNeighborsSmaller() - 1; //-1 because each quorum also includes itself 
    }

    private void listen() {
        int numSmaller = numNeighborsSmaller();
        Thread[] accepts = new Thread[numSmaller];
        int[] connectedNodes = new int[numSmaller]; //node numbers of the accepted clients
        for (int i = 0; i < numSmaller; i++) connectedNodes[i] = -1;

        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            serverSocket.setReuseAddress(true); //be able to use socket even if currently in use
            long start = System.currentTimeMillis();
            for (int i = 0; i < numSmaller; i++) { //bind to neighbors with larger IDs, doesn't actually bind to node i, but will guarantee that it calls accept() the correct number of times
                final int iCopy = i;
                accepts[i] = new Thread(() -> {
                    try {
                        Socket client = serverSocket.accept();
                        System.out.println("node " + this.nodeNumber + " accepted");
                        ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                        ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                        int nodenum = in.readInt();
                        connectedNodes[iCopy] = nodenum;
                        clientSockets[nodenum] = new Connection(client, in, out); //clientSockets[node_number], connecting client must send their node number once accepted
                        System.out.println("Node " + this.nodeNumber + " read " + nodenum + " from " + nodenum);

                        if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                            closeConnections();
                            System.out.println("Node " + this.nodeNumber + ": Timeout during listening phase");
                            return;
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        System.out.println("Failed to accept a client");
                    }
                });
                accepts[i].start();
            }

            //join all threads
            for (Thread t : accepts) t.join();
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("failed to create socket or accept()");
            closeConnections();
            return;
        }
        catch (InterruptedException e) {
            System.out.println("Unable to join a thread");
        }
        
        System.out.println("Node " + this.nodeNumber + ": listening socket successfully accepted all clients: " + Arrays.toString(connectedNodes));
    }

    private void bind() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < qMembers.length; i++) { //bind to neighbors with larger IDs
            while (true) { //if binding to a socket fails, retry until timeout
                if (System.currentTimeMillis() - start > 15000) { //timeout
                    closeConnections();
                    System.out.println("Node " + this.nodeNumber + ": Timeout during binding phase");
                    return;
                }
                try {
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
                            closeConnections();
                        }
                        System.out.println("Node " + this.nodeNumber + ": Timeout during binding phase");
                        return;
                    }
                    break; //exit while loop and repeat for all connections necessary
                }
                catch (IOException e) {
                    //wait a bit and try again
                    try {
                        Thread.sleep(500);
                    }
                    catch (InterruptedException f) {}
                }
            }
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
        try { //wait some amount of time before having clients attempt to connect
            Thread.sleep(10000); 
        }
        catch (InterruptedException e) {
            //do nothing, sleep was interrupted which isn't big deal
        }

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
