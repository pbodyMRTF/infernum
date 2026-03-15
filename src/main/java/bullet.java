import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;

class Bullet {
    // Mermi türleri
    public enum BulletType {
        AMMO,           // Pompalı için
        AMMO_SMG,       // SMG için
        AMMO_PISTOL     // Pistol için
    }

    float x, y, vx, vy;
    float speed = 600;
    boolean dead = false;
    int bounceCount = 0;
    int maxBounces = 5;
    float size = 8;

    BulletType bulletType;  // Mermi türü
    Texture texture;        // Mermi texture'ı

    Bullet(float x, float y, float angleDeg, BulletType type) {
        this.x = x - size/2;
        this.y = y - size/2;
        this.bulletType = type;

        // Mermi türüne göre texture ve hız ayarla
        switch (type) {
            case AMMO:  // Pompalı
                this.texture = Assets.getTexture(Assets.Textures.BULLET);
                this.speed = 600;
                break;
            case AMMO_SMG:  // SMG
                this.texture = Assets.getTexture(Assets.Textures.BULLET_SMG);
                this.speed = 900;
                break;
            case AMMO_PISTOL:  // Pistol
                this.texture = Assets.getTexture(Assets.Textures.BULLET_PISTOL);
                this.speed = 800;
                break;
        }

        float rad = (float)Math.toRadians(angleDeg);
        vx = (float)Math.cos(rad) * speed;
        vy = (float)Math.sin(rad) * speed;
    }

    void update(float dt, TiledMapTileLayer wallLayer, float mapWidth, float mapHeight) {
        float oldX = x;
        float oldY = y;

        x += vx * dt;
        y += vy * dt;

        // wallLayer null ise (Tutorial gibi) duvar çarpışması yok
        if (wallLayer != null) {
            float unitScale = 3f;
            float tileW = wallLayer.getTileWidth() * unitScale;
            float tileH = wallLayer.getTileHeight() * unitScale;

            int tileX = (int) (x / tileW);
            int tileY = (int) (y / tileH);

            if (tileX >= 0 && tileY >= 0 && tileX < wallLayer.getWidth() && tileY < wallLayer.getHeight()
                    && wallLayer.getCell(tileX, tileY) != null) {

                int oldTileX = (int) (oldX / tileW);

                if (oldTileX != tileX) {
                    vx = -vx;
                    x = oldX;
                } else {
                    vy = -vy;
                    y = oldY;
                }

                bounceCount++;
                if (bounceCount > maxBounces) dead = true;
            }
        }

        if (x <= 0 || x >= mapWidth - size) { vx = -vx; bounceCount++; }
        if (y <= 0 || y >= mapHeight - size) { vy = -vy; bounceCount++; }

        if (bounceCount > maxBounces) dead = true;
    }

    // Texture getter
    public Texture getTexture() {
        return texture;
    }

    // Mermi türü getter
    public BulletType getBulletType() {
        return bulletType;
    }
}