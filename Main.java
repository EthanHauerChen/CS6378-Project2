public class Main {
    public static void main (String[] args) {
        Node n = ConfigParser.parse(args[0]);
        System.out.println(n.toString());
        // n.establishConnections();
        // n.beginProtocol();
    }
}