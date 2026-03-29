public class Enemy3 implements Entity {
    float x, y;
    float speed = 256 + (float)Math.random() * 128;
    boolean dead = false;
    int hp = 8;
    int maxHp = 8;
    private com.badlogic.gdx.graphics.Texture texture;

    public Enemy3(float x, float y, com.badlogic.gdx.graphics.Texture texture) {
        this.x = x;
        this.y = y;
        this.texture = texture;
    }

    @Override public void update(float dt, float px, float py) {
        float dx = px - x, dy = py - y;
        float len = (float)Math.sqrt(dx*dx + dy*dy);
        if (len > 0) { dx /= len; dy /= len; }
        x += dx * speed * dt;
        y += dy * speed * dt;
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    @Override public boolean isDead() { return dead; }
    @Override public void setDead(boolean dead) { this.dead = dead; }
    @Override public int getHp() { return hp; }
    @Override public void setHp(int hp) { this.hp = hp; }
    @Override public int getMaxHp() { return maxHp; }
    @Override public com.badlogic.gdx.graphics.Texture getTexture() { return texture; }
}