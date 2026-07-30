package io.github.pbodyMRTF.infernum.shared;

/**
 * Sunucu konsolundan `start` / `stop` / `restart` komutu verildiğinde
 * bağlı tüm istemcilere broadcast edilir. Böylece bir istemci `start`
 * komutundan ÖNCE bağlanmış olsa bile, maçın fiilen ne zaman başladığını
 * / durduğunu öğrenebilir (JoinAckMessage.gameReady sadece join anındaki
 * durumu yansıtır, sonradan değişimi haber vermez).
 */
public class GameControlMessage {
    public boolean started;

    public GameControlMessage() {}
}