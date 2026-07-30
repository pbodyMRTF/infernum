package io.github.pbodyMRTF.infernum.shared;
import com.esotericsoftware.kryonet.EndPoint;
import java.util.ArrayList;
public class NetworkRegistry {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777; // kullanılmıyor
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
        // YENİ: start/stop/restart konsol komutlarında istemcilere
        // maç durumunu bildirmek için kullanılan mesaj (GameServer.broadcastControl).
        // ÖNEMLİ: mevcut kayıtların ALTINA eklendi, aralarına değil —
        // Kryo id ataması sıraya göre yapıldığı için var olan kayıtların
        // sırasını bozmak client/server arasında uyumsuzluğa yol açar.
        kryo.register(GameControlMessage.class);
        kryo.setReferences(false);
        kryo.setRegistrationRequired(true);
    }
}