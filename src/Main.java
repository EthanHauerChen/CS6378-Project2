public class Main {
    public static void main (String[] args) {
        if (args.length != 1) {
            System.out.println("usage: java Main config-file");
            return;
        }
        Node n = ConfigParser.parse(args[0]);
        //System.out.println(n.toString());
        n.establishConnections();
        // n.beginProtocol();
    }
}