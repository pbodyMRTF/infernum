public class Enemy3 {
    float x, y;
    float speed = 256 + (float)Math.random() * 128;
    boolean dead = false;
    int hp = 8;
    int maxHp = 8;
    public Enemy3(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void update(float dt, float px, float py) {
        float dx = px - x;
        float dy = py - y;
        float len = (float)Math.sqrt(dx*dx + dy*dy);
        if (len > 0) {
            dx /= len;
            dy /= len;
        }
        x += dx * speed * dt;
        y += dy * speed * dt;
    }

    public boolean isDead() {
        return dead;
    }
}