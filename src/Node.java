import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

public class Node {
    String hostname;
    int port;
    int nodeNumber;
    int interRequestDelay;
    int csExecutionTime;
    int numRequests;
    HashMap<Integer, Neighbor> qMembers;
    PriorityQueue<Request> requestQueue;
    private int clock;

    private class Request implements Comparable {
        int nodeNumber;
        int timestamp;
        
        public Request(int nodenum, int timestamp) {
            this.nodeNumber = nodenum;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(Object o) {
            Request temp = (Request)o;
            if (this.timestamp < temp.timestamp) return -1;
            else if (this.timestamp > temp.timestamp) return 1;
            else if (this.nodeNumber < temp.nodeNumber) return -1;
            else return 1;
        }

        @Override
        public boolean equals(Object o) {
            Request temp = (Request)o;
            return this.nodeNumber == temp.nodeNumber; //impossible for multiple requests from same node at the same time since csEnter is blocking
        }
    }

    public Node(String hostname, int port, int nodenum, int interRequestDelay, int csExecutionTime, int numRequests, Neighbor[] qMembers) {
        this.hostname = hostname;
        this.port = port;
        this.nodeNumber = nodenum;
        this.interRequestDelay = interRequestDelay;
        this.csExecutionTime = csExecutionTime;
        this.numRequests = numRequests;
        addQMembers(qMembers);
        clock = 0;
    }
    private void addQMembers (Neighbor[] members) {
        qMembers = new HashMap<Integer, Neighbor>(members.length);
        for (Neighbor n : members) {
            this.qMembers.put(n.nodeNumber, n);
        }
    }

    public void closeConnections() {
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
            System.out.println("Node " + this.nodeNumber + " ready and listening");
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
        for (Neighbor neighbor : this.qMembers.values()) { //bind to neighbors with larger IDs
            while (true) { //if binding to a socket fails, retry until timeout
                if (System.currentTimeMillis() - start > 15000) { //timeout
                    closeConnections();
                    System.out.println("Node " + this.nodeNumber + ": Timeout during binding phase");
                    return;
                }
                try {
                    if (neighbor.nodeNumber > this.nodeNumber) { //if this.nodeNumber < neighbor, bind socket
                        Socket client = new Socket(neighbor.hostname, neighbor.port);
                        ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                        neighbor.addConnection(new Connection(client, in, out));
                        neighbor.connection.writeInt(this.nodeNumber); //once connected, send node_number as initial message
                        neighbor.connection.flush();
                        System.out.println("Node " + this.nodeNumber + " wrote " + this.nodeNumber + " to " + neighbor.nodeNumber);
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

    public void establishConnections() {
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
            e.printStackTrace();
            return;
        }

        System.out.println("Node " + this.nodeNumber + " establishConnections terminated");
        return;
    }
    
    private void incrementClock() { clock++; }

    private boolean canEnter() {
        for (Neighbor n : this.qMembers.values()) {
            if (!n.granted) return false;
        }
        return true;
    }

    private void csEnter() {
        sendMessage(MessageType.REQUEST, clock); //send CS request to all quorum members

        //enter CS if grant from all qMembers
        boolean canEnter = true;
        while(!canEnter()) {
            try { //retry after waiting .1 seconds
                Thread.sleep(100); 
            }
            catch (InterruptedException e) {}
        }
    
        //enter CS
    }

    private void csLeave() {
        sendMessage(MessageType.RELEASE); //send message informing other processes that CS is no longer in use
        return;
    }

    private void sendMessage(MessageType type) {
        sendMessage(type, -1);
    }
    private void sendMessage(MessageType type, int clock) {
        if (type == MessageType.REQUEST || type == MessageType.RELEASE) {
            for (Neighbor n : this.qMembers.values()) {
                if (n.nodeNumber == this.nodeNumber) continue;
                if (!n.connection.writeMessage(new Message(type, clock))) {
                    closeConnections();
                    System.out.println(this.nodeNumber + " failed to write message, abort protocol");
                    System.exit(-1);
                }
            }
        }
    }
    private void sendMessage(MessageType type, int clock, int dest) {
        if (!this.qMembers.get(dest).connection.writeMessage(new Message(type, clock))) {
            closeConnections();
            System.out.println(this.nodeNumber + " failed to write message, abort protocol");
            System.exit(-1);
        }
    }

    private boolean readMessage() {}

    public void beginProtocol() {
        /** Thread for requesting critical section from quorum members*/
        Thread cs = new Thread(() -> {
            for (int i = 0; i < this.numRequests; i++) {
                csEnter(); //blocking request for critical section
                while (true) {
                    try {
                        Thread.sleep(this.csExecutionTime);
                        break;
                    }
                    catch (InterruptedException e) {
                        System.out.println("cs execution interrupted, try again");
                    }
                }
                csLeave(); //inform other nodes that critical section is available

                try {
                    Thread.sleep(this.interRequestDelay);
                }
                catch (InterruptedException e) {
                    System.out.println("interRequestDelay interrupted, proceeding to enter cs again if num requests made has not exceeded maximum");
                }
            }
        });
        cs.start();

        /** Thread for granting critical section requests to membership set*/
        Thread read = new Thread(() -> {
            int numExited = 0; //number of EXIT messages received
            while (numExited < qMembers.size()) {
                for (Neighbor n : this.qMembers.values()) {
                    if (n.nodeNumber == this.nodeNumber) continue;
                    Message msg = readMessage();
                    if (msg == null) continue;
                    switch (msg.msgType) {
                        case REQUEST:
                            requestQueue.add(new Request(n.nodeNumber, msg.clock));
                            if (requestQueue.peek().nodeNumber == n.nodeNumber) sendMessage(MessageType.GRANT);
                            break;
                        case GRANT:
                            n.granted = true;
                            break;
                        case RELEASE:
                            requestQueue.remove(new Request(n.nodeNumber, -1));
                            break;
                    }
                }
            }
        });

        cs.join();
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
