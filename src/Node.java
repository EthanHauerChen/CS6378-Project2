import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

public class Node {
    String hostname;
    int port;
    int nodeNumber;
    int interRequestDelay;
    int csExecutionTime;
    int numRequests;
    HashMap<Integer, Neighbor> qMembers;

    public Node(String hostname, int port, int nodenum, int interRequestDelay, int csExecutionTime, int numRequests, Neighbor[] qMembers) {
        this.hostname = hostname;
        this.port = port;
        this.nodeNumber = nodenum;
        this.interRequestDelay = interRequestDelay;
        this.csExecutionTime = csExecutionTime;
        this.numRequests = numRequests;
        addQMembers(qMembers);
    }
    private void addQMembers (Neighbor[] members) {
        qMembers = new HashMap<Integer, Neighbor>(members.length);
        for (Neighbor n : members) {
            this.qMembers.put(n.nodeNumber, n);
        }
    }

    private void closeConnections() {
        for (Neighbor n : qMembers.values()) {
            try {
                if (n.connection != null)
                    n.connection.close();
            }
            catch(IOException e) {
                //do nothing, failure to close is no big deal
            }
        }
    }

    private int numNeighborsSmaller() { //returns number of neighors with smaller node number than this
        int numSmaller = 0;
        for (Neighbor n : this.qMembers.values()) {
            if (n.nodeNumber < this.nodeNumber) numSmaller++;
        }
        return numSmaller;
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
                Socket client = serverSocket.accept();
                final int iCopy = i;
                accepts[i] = new Thread(() -> {
                    try {
                        ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                        int nodenum = in.readInt();
                        connectedNodes[iCopy] = nodenum;
                        qMembers.get(nodenum).addConnection(new Connection(client, in, out)); //connecting client must send their node number once accepted
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
        for (int i = 0; i < qMembers.size(); i++) { //bind to neighbors with larger IDs
            while (true) { //if binding to a socket fails, retry until timeout
                if (System.currentTimeMillis() - start > 15000) { //timeout
                    closeConnections();
                    System.out.println("Node " + this.nodeNumber + ": Timeout during binding phase");
                    return;
                }
                try {
                    Neighbor neighborI = this.qMembers.get(i);
                    if (neighborI.nodeNumber > this.nodeNumber) { //if this.nodeNumber < neighbor, bind socket
                        Socket client = new Socket(neighborI.hostname, neighborI.port);
                        ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                        neighborI.addConnection(new Connection(client, in, out));
                        neighborI.connection.writeInt(this.nodeNumber); //once connected, send node_number as initial message
                        neighborI.connection.flush();
                        System.out.println("Node " + this.nodeNumber + " wrote " + this.nodeNumber + " to " + neighborI.nodeNumber);
                    }

                    if (System.currentTimeMillis() - start > 15000) { //if timeout, then close all connections, exit
                        closeConnections();
                        System.out.println("Node " + this.nodeNumber + ": Timeout during binding phase");
                        return;
                    }
                    break; //exit while loop and repeat for all connections necessary
                }
                catch (IOException e) {
                    //wait a bit and try again
                    try {
                        Thread.sleep(500);
                        System.out.println("Node " + this.nodeNumber + " retry");
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
            Thread.sleep(1000); 
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
        "quorum members: " + qMembers.toString() + 
        "\n}";
    }
}
