import java.io.Serializable;

/** Message object that will be sent using sockets */
public class Message implements Serializable {
    MessageType msgType;
    int clock;
    int nodeNumber;

    public Message(MessageType msg, int clock, int nodeNumber) {
        this.msgType = msg;
        this.clock = clock;
        this.nodeNumber = nodeNumber;
    }

    @Override
    public String toString() {
        return "Message(type: " + msgType + ", clock: " + clock + ")";
    }
}
