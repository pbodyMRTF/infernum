package shared;

public enum MessageType {
    JOIN,           // Client → Server: oyuna katıl
    JOIN_ACK,       // Server → Client: kabul, sana playerId X verildi
    INPUT,          // Client → Server: her frame input snapshot
    GAME_STATE,     // Server → Client: her tick tam oyun durumu
    PLAYER_DIED,    // Server → Client: bir oyuncu öldü
    GAME_OVER,      // Server → Client: ikisi de öldü
    DISCONNECT      // her iki yön
}