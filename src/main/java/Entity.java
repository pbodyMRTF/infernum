public interface Entity {
    void update(float dt, float px, float py);
    float getX();
    float getY();
    boolean isDead();
    void setDead(boolean dead);
    int getHp();
    void setHp(int hp);
    int getMaxHp();
    com.badlogic.gdx.graphics.Texture getTexture();
}