import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.Arrays;

public class ConfigParser {
    public static Node parse(String filename) {
        Node n = new Node();
        try {
            BufferedReader file = new BufferedReader(new FileReader(filename));
            String line = file.readLine();
            String[] tokens = line.split(" ");
            String hostname = InetAddress.getLocalHost().getHostName();
            n.hostname = hostname;

            //parse first line
            int numNodes = Integer.parseInt(tokens[0]);
            n.interRequestDelay = Integer.parseInt(tokens[1]);
            n.csExecutionTime = Integer.parseInt(tokens[2]);
            n.numRequests = Integer.parseInt(tokens[3]);

            //parse next n lines
            //temporary arrays that store node info so neighbor info can be extracted later
            String[] nodenames = new String[numNodes];
            int[] ports = new int[numNodes];
            for (int i = 0; i < numNodes; i++) {
                line = file.readLine();
                if (line.isEmpty() || !Character.isDigit(line.charAt(0))) { //invalid line, go to next line
                    i--;
                    continue;
                }
                tokens = line.split(" ");
                if (hostname.contains(tokens[1])) {
                    n.nodeNumber = Integer.parseInt(tokens[0]);
                    n.port = Integer.parseInt(tokens[2]);

                }
                else { //hostname and port num for node i
                    nodenames[i] = tokens[1];
                    ports[i] = Integer.parseInt(tokens[2]);
                }
            }
            //parse final n lines
            //go to the line containing quorum members of the node
            for (int i = 0; i <= n.nodeNumber; i++) {
                line = file.readLine();
                if (line.isEmpty() || !Character.isDigit(line.charAt(0))) { //invalid line, go to next line
                    i--;
                    continue;
                }
            }
            tokens = line.split(" ");
            n.qMembers = new Neighbor[tokens.length];
            System.out.println(Arrays.toString(tokens));
            for (int i = 0; i < tokens.length; i++) {
                int neighborNum = Integer.parseInt(tokens[i]);
                n.qMembers[i] = new Neighbor(neighborNum, nodenames[neighborNum], ports[neighborNum]);
            }
        } 
        catch (FileNotFoundException e) {
            System.out.println("Unable to read file " + filename);
        } 
        catch (IOException e) {
            System.out.println("Error reading file");
        }
        return n;
    }
}
