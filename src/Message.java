import java.io.Serializable;

/** Message object that will be sent using sockets */
public class Message implements Serializable {
    MessageType msgType;
    int clock;

    public Message(MessageType msg, int clock) {
        this.msgType = msg;
        this.clock = clock;
    }
}
