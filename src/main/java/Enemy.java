public class Enemy {
    float x, y;
    float speed = 200 + (float)Math.random() * 80;
    boolean dead = false;
    int hp = 15;
    int maxHp = 15;

    public Enemy(float x, float y) {
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