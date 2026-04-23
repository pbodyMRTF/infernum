package shared;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import shared.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkClient {

    private Client client;
    private int myPlayerId = -1;
    private boolean gameReady = false;

    // Son gelen state — thread-safe
    private final AtomicReference<GameState> latestState = new AtomicReference<>(null);

    public interface ReadyCallback {
        void onGameReady(int playerId);
    }
    private ReadyCallback readyCallback;

    public NetworkClient(ReadyCallback readyCallback) {
        this.readyCallback = readyCallback;
    }

    public void connect(String host) throws IOException {
        client = new Client(65536, 65536);
        NetworkRegistry.register(client);

        client.addListener(new Listener() {
            @Override
            public void received(Connection c, Object obj) {
                if (obj instanceof JoinAckMessage) {
                    JoinAckMessage ack = (JoinAckMessage) obj;
                    myPlayerId = ack.assignedPlayerId;
                    if (ack.gameReady) {
                        gameReady = true;
                        if (readyCallback != null)
                            readyCallback.onGameReady(myPlayerId);
                    }
                } else if (obj instanceof GameState) {
                    latestState.set((GameState) obj);
                }
            }

            @Override
            public void disconnected(Connection c) {
                System.out.println("Server bağlantısı kesildi.");
            }
        });

        client.start();
        client.connect(5000, host, NetworkRegistry.TCP_PORT);

        JoinMessage join = new JoinMessage();
        join.playerName = "Player";
        client.sendTCP(join);
    }

    public void sendInput(PlayerInput input) {
        if (client != null && client.isConnected()) {
            client.sendTCP(input);
        }
    }

    public GameState pollState() {
        return latestState.getAndSet(null);
    }

    public boolean isGameReady() { return gameReady; }
    public int getMyPlayerId()   { return myPlayerId; }

    public void disconnect() {
        if (client != null) client.stop();
    }
}