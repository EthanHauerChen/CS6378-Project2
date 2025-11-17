import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Connection {
    private Socket socket; //only stored so that close() can be called. should not interface with socket directly, only with in and out
    InputStream in;
    OutputStream out;

    public Connection(Socket s, InputStream i, OutputStream o) {
        socket = s;
        in = i;
        out = o;
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
                this.flush();
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
