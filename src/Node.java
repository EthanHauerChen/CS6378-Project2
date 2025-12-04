import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
//import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class Node {
    String hostname;
    int port;
    int nodeNumber;
    int interRequestDelay;
    int csExecutionTime;
    int numRequests;
    //int granted; //this.nodeNumber if no processes need grant, else node number of process being granted
    HashMap<Integer, Neighbor> qMembers;
    LinkedBlockingQueue<Message> inbox; //queue that stores all incoming messages
    PriorityBlockingQueue<Request> requestQueue; 
    private int clock;
    private boolean hasFailed;

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

        @Override
        public String toString() {
            return "(node: " + this.nodeNumber + ", timestamp: " + this.timestamp + ")";
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
        inbox = new LinkedBlockingQueue<>();
        requestQueue = new PriorityBlockingQueue<>();
        this.qMembers.get(this.nodeNumber).granted = true;
        hasFailed = false;
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
                        out.flush();
                        ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                        int nodenum = -1;
                        try { nodenum = (Integer) in.readObject(); } catch (ClassNotFoundException e) {}
                        connectedNodes[iCopy] = nodenum;
                        qMembers.get(nodenum).addConnection(new Connection(client, in, out, inbox)); //connecting client must send their node number once accepted
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
                        out.flush();
                        ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                        neighbor.addConnection(new Connection(client, in, out, inbox));
                        out.writeObject((Integer)this.nodeNumber); //once connected, send node_number as initial message
                        out.flush();
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

        System.out.println("Node " + this.nodeNumber + ": successfully binded to all clients");
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

        try {
            Thread.sleep(10000);
        }
        catch (InterruptedException e) {}

        System.out.println("Node " + this.nodeNumber + " establishConnections terminated");
        return;
    }
    
    private void incrementClock() { clock++; }

    private boolean canEnter() {
        // if (requestQueue.peek().nodeNumber != this.nodeNumber) {
        //     //if (this.nodeNumber == 3) {
        //     System.out.print(this.nodeNumber + " canEnter false. not at top of queue: ");
        //     printQueue();
        //     //}
        //     return false;
        // }
        boolean returnVal = true;
        ArrayList<Integer> notGranted = new ArrayList<>();
        for (Neighbor n : this.qMembers.values()) {
            if (!n.granted) {
                //if (this.nodeNumber == 3) {
                notGranted.add(n.nodeNumber);
                //}
                returnVal = false;
            }
        }
        if (notGranted.size() > 0) {
            System.out.print(this.nodeNumber + " canEnter false. " + notGranted.toString() + " not granted. queue: ");
            printQueue();
        }
        return returnVal && this.qMembers.get(this.nodeNumber).granted;
    }

    private boolean csEnter() {
        broadcastMessage(MessageType.REQUEST, clock); //send CS request to all quorum members
        requestQueue.add(new Request(this.nodeNumber, clock++)); //add own request to queue
        long start = System.currentTimeMillis();

        //enter CS if grant from all qMembers
        while(!canEnter()) {
            try { //retry after waiting .1 seconds
                Thread.sleep(100); 
            }
            catch (InterruptedException e) {}
            // if (System.currentTimeMillis() - start > 15000) { //timeout
            //     attemptExit();
            //     closeConnections();
            //     System.out.println(this.nodeNumber + " timeout inside csEnter");
            //     return false;
            // }
        }
    
        //enter CS
        return true;
    }

    private void csLeave() {
        hasFailed = false;
        broadcastMessage(MessageType.RELEASE); //send message informing other processes that CS is no longer in use
        for (Neighbor n : this.qMembers.values()) n.granted = false;
        requestQueue.remove(new Request(this.nodeNumber, this.clock)); //remove own request from queue. can use timestamp -1 since equals() only compares nodeNumber, and that is fine because only 1 of this node's requests can be in queue at a time
        Request nextReq = requestQueue.peek();
        System.out.print(this.nodeNumber + " csLeave. Queue after removing own req: ");
        printQueue();
        if (!requestQueue.isEmpty() && nextReq.nodeNumber != this.nodeNumber) {
            sendMessage(MessageType.GRANT, this.clock, nextReq.nodeNumber);
            if (this.nodeNumber == 0 && nextReq.nodeNumber == 3) {
                System.out.println(nextReq.nodeNumber + ": " + this.qMembers.get(nextReq));
            }
        }
        return;
    }

    private void broadcastMessage(MessageType type) {
        broadcastMessage(type, this.clock);
    }
    private void broadcastMessage(MessageType type, int clock) {
        if (type == MessageType.REQUEST || type == MessageType.RELEASE) {
            for (Neighbor n : this.qMembers.values()) {
                if (n.nodeNumber == this.nodeNumber) continue;
                if (!n.connection.writeMessage(new Message(type, clock))) {
                    attemptExit();
                    closeConnections();
                    System.out.println(this.nodeNumber + " failed to write message, abort protocol");
                    System.exit(-1);
                }
                else {
                    printDebug(n.nodeNumber, type);
                }
            }
        }
    }
    private void sendMessage(MessageType type, int clock, int dest) {
        if (this.nodeNumber == 0 && dest == 3) System.out.println(this.nodeNumber + " sending " + new Message(type, clock));
        if (!this.qMembers.get(dest).connection.writeMessage(new Message(type, clock))) {
            attemptExit();
            closeConnections();
            System.out.println(this.nodeNumber + " failed to write message, abort protocol");
            System.exit(-1);
        }
        printDebug(dest, type);
    }
    private void attemptExit() {
        for (Neighbor n : this.qMembers.values()) {
            if (n.nodeNumber == this.nodeNumber) continue;
            n.connection.writeMessage(new Message(MessageType.EXIT, this.clock));
        }
    }

    private Message readMessage(Neighbor n) {
        return n.connection.readMessage();
    }

    private void printDebug(int to, MessageType msg) {
        System.out.print("node " + this.nodeNumber + " " + msg + " to " + to + ", Queue" + this.nodeNumber + ": ");
        printQueue();
    }
    private void printQueue() {
        System.out.println(requestQueue.toString() + ", clock: " + this.clock);
    }
    public void beginProtocol() {
        /** Thread for requesting critical section from quorum members*/
        Thread cs = new Thread(() -> {
            for (int i = 0; i < this.numRequests; i++) {
                if(!csEnter()) { //blocking request for critical section
                    System.out.println(this.nodeNumber + " failed to enter CS, abort");
                    return;
                }
                System.out.println(this.nodeNumber + " entered CS");
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
            broadcastMessage(MessageType.EXIT);
        });

        Thread[] read = new Thread[this.qMembers.size() - 1]; //do not include yourself, hence -1
        int i = 0;
        for (Neighbor n : this.qMembers.values()) {
            if (n.nodeNumber == this.nodeNumber) continue;
            read[i] = new Thread(() -> {
                long start = System.currentTimeMillis();
                boolean rcvdExit = false;
                while (!rcvdExit) { 
                    Message msg = readMessage(n);
                    if (msg == null) {
                        try { //retry after waiting .01 seconds
                            Thread.sleep(10); 
                            continue;
                        }
                        catch (InterruptedException e) {}
                    }
                    //if (this.nodeNumber == 3 && n.nodeNumber == 0) {
                        System.out.println(this.nodeNumber + " reading non-null message from: " + n.nodeNumber + ": " + msg.toString());
                    //}
                    switch (msg.msgType) {
                        case REQUEST:
                            Request oldReq = requestQueue.peek();
                            Request newReq = new Request(n.nodeNumber, msg.clock);
                            this.clock = Math.max(this.clock, newReq.timestamp) + 1;
                            requestQueue.add(newReq);
                            if (oldReq == null) {
                                //printDebug(newReq.nodeNumber, MessageType.GRANT);
                                sendMessage(MessageType.GRANT, this.clock, newReq.nodeNumber); //if only request in queue, grant
                            }
                            else if (oldReq.compareTo(newReq) > 0) {
                                if (oldReq.nodeNumber == this.nodeNumber && !canEnter()) { // if own request at top of queue but cannot enter
                                    this.qMembers.get(this.nodeNumber).granted = false;
                                    hasFailed = true;
                                    //printDebug(newReq.nodeNumber, MessageType.GRANT);
                                    sendMessage(MessageType.GRANT, this.clock, newReq.nodeNumber);
                                }
                                else if (oldReq.nodeNumber != this.nodeNumber) {
                                    //printDebug(oldReq.nodeNumber, MessageType.INQUIRE);
                                    sendMessage(MessageType.INQUIRE, this.clock, oldReq.nodeNumber);
                                }
                            }
                            else {
                                //printDebug(newReq.nodeNumber, MessageType.FAILED);
                                sendMessage(MessageType.FAILED, this.clock, newReq.nodeNumber);
                            }
                            break;
                        case GRANT:
                            n.granted = true;
                        case YIELD:
                            Request topReq = requestQueue.peek();
                            if (topReq != null) {
                                if (topReq.nodeNumber == this.nodeNumber) {
                                    n.granted = true;
                                }
                                else {
                                    //might have to be a grant message
                                    sendMessage(MessageType.GRANT, this.clock, topReq.nodeNumber);
                                }
                            }
                            //if (this.nodeNumber == 3) 
                                System.out.println(this.nodeNumber + " GRANTED from " + n.nodeNumber + ", n.granted = " + n.granted);
                            break;
                        case RELEASE:
                            /** can't simply remove top of queue since the process that sent the release message is not guaranteed
                            to be at the top of queue. For example, if a process enters the CS and then later a quorum member receives a request with a smaller
                            timestamp, then there would be a smaller timestamp in the queue*/
                            requestQueue.remove(new Request(n.nodeNumber, -1)); 
                            if (requestQueue.isEmpty() || requestQueue.peek().nodeNumber == this.nodeNumber) {
                                this.qMembers.get(this.nodeNumber).granted = true;
                                //maybe (as well, not replace above): this.qMembers.get(n.nodeNumber).granted = true;
                            }
                            else {
                                sendMessage(MessageType.GRANT, this.clock, requestQueue.peek().nodeNumber);
                            }
                            break;
                        case INQUIRE:
                            //if (this.nodeNumber == 3) {
                                System.out.print("3 INQUIRE from " + n.nodeNumber + ". hasFailed = " + hasFailed + " ");
                                printQueue();
                            //}
                            if (hasFailed) {
                                n.granted = false;
                                //printDebug(n.nodeNumber, MessageType.YIELD);
                                sendMessage(MessageType.YIELD, this.clock, n.nodeNumber);
                            }
                            break;
                        case FAILED:
                            n.granted = false; //probably not necessary
                            hasFailed = true;
                            if (!requestQueue.isEmpty() && requestQueue.peek().nodeNumber != this.nodeNumber) { //this node has failed, will not obtain ME yet, so yield to previously INQUIREd process
                                //printDebug(requestQueue.peek().nodeNumber, MessageType.YIELD);
                                sendMessage(MessageType.GRANT, this.clock, requestQueue.peek().nodeNumber);
                            }
                            break;
                        case EXIT:
                            rcvdExit = true;
                            break;
                        }
                    }
                    try { //retry after waiting .01 seconds
                        Thread.sleep(10); 
                    }
                    catch (InterruptedException e) {}

                    // if (System.currentTimeMillis() - start > 15000) { //timeout
                    //     attemptExit();
                    //     closeConnections();
                    //     System.out.println(this.nodeNumber + " timeout inside the read thread");
                    //     return;
                    // }
            });
            i++;
        }

        /** Thread for granting critical section requests to membership set*/
        // Thread read = new Thread(() -> {
        //     long start = System.currentTimeMillis();
        //     int numExited = 0; //number of EXIT messages received
        //     boolean hasFailed = false; //if received a failed message from another quorum member
        //     while (numExited < qMembers.size() - 1) { //-1 because this node is also part of qMembers
        //         for (Neighbor n : this.qMembers.values()) {
        //             if (n.nodeNumber == this.nodeNumber) continue;
        //             Message msg = readMessage(n);
        //             if (msg == null) continue;
        //             if (this.nodeNumber == 3 && n.nodeNumber == 0) {
        //                 System.out.println(this.nodeNumber + " reading non-null message from: " + n.nodeNumber + ": " + msg.toString());
        //             }
        //             switch (msg.msgType) {
        //                 case REQUEST:
        //                     Request oldReq = requestQueue.peek();
        //                     Request newReq = new Request(n.nodeNumber, msg.clock);
        //                     this.clock = Math.max(this.clock, newReq.timestamp) + 1;
        //                     requestQueue.add(newReq);
        //                     if (oldReq == null) {
        //                         //printDebug(newReq.nodeNumber, MessageType.GRANT);
        //                         sendMessage(MessageType.GRANT, -1, newReq.nodeNumber); //if only request in queue, grant
        //                     }
        //                     else if (oldReq.compareTo(newReq) > 0) {
        //                         if (oldReq.nodeNumber == this.nodeNumber && !canEnter()) { // if own request at top of queue but cannot enter
        //                             this.qMembers.get(this.nodeNumber).granted = false;
        //                             hasFailed = true;
        //                             //printDebug(newReq.nodeNumber, MessageType.GRANT);
        //                             sendMessage(MessageType.GRANT, -1, newReq.nodeNumber);
        //                         }
        //                         else if (oldReq.nodeNumber != this.nodeNumber) {
        //                             //printDebug(oldReq.nodeNumber, MessageType.INQUIRE);
        //                             sendMessage(MessageType.INQUIRE, -1, oldReq.nodeNumber);
        //                         }
        //                     }
        //                     else {
        //                         //printDebug(newReq.nodeNumber, MessageType.FAILED);
        //                         sendMessage(MessageType.FAILED, -1, newReq.nodeNumber);
        //                     }
        //                     break;
        //                 case GRANT:
        //                 case YIELD:
        //                     n.granted = true;
        //                     if (this.nodeNumber == 3) System.out.println(this.nodeNumber + " GRANTED from " + n.nodeNumber + ", n.granted = " + n.granted);
        //                     break;
        //                 case RELEASE:
        //                     /** can't simply remove top of queue since the process that sent the release message is not guaranteed
        //                     to be at the top of queue. For example, if a process enters the CS and then later a quorum member receives a request with a smaller
        //                     timestamp, then there would be a smaller timestamp in the queue*/
        //                     requestQueue.remove(new Request(n.nodeNumber, -1)); 
        //                     if (requestQueue.isEmpty() || requestQueue.peek().nodeNumber == this.nodeNumber) {
        //                         this.qMembers.get(this.nodeNumber).granted = true;
        //                         //maybe (as well, not replace above): this.qMembers.get(n.nodeNumber).granted = true;
        //                     }
        //                     else {
        //                         sendMessage(MessageType.GRANT, -1, requestQueue.peek().nodeNumber);
        //                     }
        //                     break;
        //                 case INQUIRE:
        //                     if (hasFailed) {
        //                         n.granted = false;
        //                         //printDebug(n.nodeNumber, MessageType.YIELD);
        //                         sendMessage(MessageType.YIELD, -1, n.nodeNumber);
        //                     }
        //                     break;
        //                 case FAILED:
        //                     n.granted = false; //probably not necessary
        //                     hasFailed = true;
        //                     if (!requestQueue.isEmpty() && requestQueue.peek().nodeNumber != this.nodeNumber) { //this node has failed, will not obtain ME yet, so yield to previously INQUIREd process
        //                         //printDebug(requestQueue.peek().nodeNumber, MessageType.YIELD);
        //                         sendMessage(MessageType.YIELD, -1, requestQueue.peek().nodeNumber);
        //                     }
        //                     break;
        //                 case EXIT:
        //                     numExited++;
        //                     break;
        //             }
        //         }

        //         // if (System.currentTimeMillis() - start > 15000) { //timeout
        //         //     attemptExit();
        //         //     closeConnections();
        //         //     System.out.println(this.nodeNumber + " timeout inside the read thread");
        //         //     return;
        //         // }
        //     }
        // });

        cs.start();
        for (Thread r : read) r.start();

        try {
            cs.join();
            for (Thread r : read) r.join();
        }
        catch (InterruptedException e) {
            System.out.println("Unable to join cs or read thread");
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
        "quorum members: " + qMembers.toString() + 
        "\n}";
    }
}
