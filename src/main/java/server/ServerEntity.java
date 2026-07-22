package server;

public class ServerEntity {
    public int id;
    public byte type; // EntitySnapshot sabitlerini kullan
    public float x, y;
    public float speed;
    public int hp;
    public int maxHp;
    public boolean dead;

    public ServerEntity(int id, byte type, float x, float y, float speed, int hp) {
        this.id    = id;
        this.type  = type;
        this.x     = x;
        this.y     = y;
        this.speed = speed;
        this.hp    = hp;
        this.maxHp = hp;
    }

    public void update(float dt, float targetX, float targetY) {
        if (dead) return;
        float dx = targetX - x;
        float dy = targetY - y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0) { dx /= len; dy /= len; }
        x += dx * speed * dt;
        y += dy * speed * dt;
    }
}