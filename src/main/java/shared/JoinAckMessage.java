package shared;

public class JoinAckMessage {
    public int assignedPlayerId;   // 0 = host, 1 = joiner
    public boolean gameReady;      // ikinci oyuncu bağlandığında true
    public JoinAckMessage() {}
}