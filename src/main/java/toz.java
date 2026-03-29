import com.badlogic.gdx.graphics.Texture;

public class toz {
    float x, y;
    float vx, vy;
    float life = 0.5f; // saniye
    Texture tex;
    boolean dead = false;
    float lifetime = 0;

    public toz(float x, float y, Texture tex) {
        this.x = x;
        this.y = y;
        this.tex = tex;
        float angle = (float)(Math.random() * 2 * Math.PI);
        float speed = 50 + (float)(Math.random() * 100);
        vx = (float)Math.cos(angle) * speed;
        vy = (float)Math.sin(angle) * speed;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        lifetime += dt;

        if (lifetime > 1.5f) {
            dead = true;
        }

    }

    public boolean isDead() {
        return dead;
    }
}
