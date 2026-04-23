package shared;

import com.esotericsoftware.kryonet.EndPoint;
import java.util.ArrayList;

public class NetworkRegistry {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;

    public static void register(EndPoint endPoint) {
        var kryo = endPoint.getKryo();

        kryo.register(MessageType.class);
        kryo.register(PlayerInput.class);
        kryo.register(GameState.class);
        kryo.register(PlayerSnapshot.class);
        kryo.register(EntitySnapshot.class);
        kryo.register(BulletSnapshot.class);
        kryo.register(JoinMessage.class);
        kryo.register(JoinAckMessage.class);
        kryo.register(ArrayList.class);
    }
}