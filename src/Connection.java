import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class Connection {
    private Socket socket; //only stored so that close() can be called. should not interface with socket directly, only with in and out
    BufferedReader in;
    PrintWriter out;

    public Connection(Socket s, BufferedReader i, PrintWriter o) {
        socket = s;
        in = i;
        out = o;
    }

    public void close() throws IOException {
        if (socket != null) socket.close();
        if (in != null) in.close();
        if (out != null) out.close();
    }

}
