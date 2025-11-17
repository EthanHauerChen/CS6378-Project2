/** Message object that will be sent using sockets */
public class Message {
    MessageType msgType;
    int clock;

    public Message(MessageType msg, int clock) {
        this.msgType = msg;
        this.clock = clock;
    }
}
