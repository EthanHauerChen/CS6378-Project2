import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;

public class Connection {
    private Socket socket; //only stored so that close() can be called. should not interface with socket directly, only with in and out
    final ObjectInputStream in;
    final ObjectOutputStream out;

    private BlockingQueue<Message> inbox;

    public Connection(Socket s, ObjectInputStream i, ObjectOutputStream o, BlockingQueue<Message> inbox) {
        socket = s;
        in = i;
        out = o;
        this.inbox = inbox;

        startReaderThread();
    }

    private void startReaderThread() {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Message msg = (Message) in.readObject(); // safe to block here
                    inbox.add(msg);
                }
            } catch (SocketException e) {
                // connection closed normally
            } catch (EOFException e) {
                // remote side closed
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        });

        t.start();
    }

    //non blocking
    public Message getNextMessage() {
        if (inbox.isEmpty()) return null;
        return inbox.poll();
    }

    // Blocking write
    public synchronized void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    public void flush() throws IOException {
        out.flush();
    }

    public int readInt() throws IOException {
        if (in instanceof ObjectInputStream) return readIntObject();
        else { //else if this.in is a DataInputStream or some other type of InputStream
            /** this is meant for increasing reusability/robustness of Connection.readInt() so that it
             * works for all InputStream types, but that will take to much time, so this will portion will be 
             * implemented only if necessary
             */
            //read 4 bytes or something
            return readIntObject(); //read integer using DataInputStream or something
        }
    }
    private int readIntObject() throws IOException { 
        return ((ObjectInputStream)in).readInt();
    }

    public Message readMessage() {
        try {
            Message m = (Message) (((ObjectInputStream)in).readObject());
            return m;
        }
        catch (SocketTimeoutException e) {
            //e.printStackTrace();
            //no data available right now
            return null;
        }
        catch (IOException | ClassNotFoundException e) {
            System.out.println("Something went wrong reading message");
            e.printStackTrace();
            return null;
        }
    }

    public void writeInt(int output) throws IOException {
        if (out instanceof ObjectOutputStream) ((ObjectOutputStream)out).writeInt(output);
        else {
            /**
             * same deal as else statement in readInt()
             */
            ((ObjectOutputStream)out).writeInt(output);
        }
        out.flush();
    }

    public boolean writeMessage(Message msg) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10000) {
            try {
                ((ObjectOutputStream)out).writeObject(msg);
                this.out.flush();
                return true;
            }
            catch (IOException e) {
                System.out.println("Failed to write message, try again");
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException f) {}
            }
        }

        System.out.println("Failed to write message");
        return false;
    }

    public void close() throws IOException {
        if (socket != null) socket.close();
        if (in != null) in.close();
        if (out != null) out.close();
    }

}
