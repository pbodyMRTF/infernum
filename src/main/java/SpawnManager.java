import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.utils.Array;

public class SpawnManager {
    private EntityManager entityManager;
    private com.badlogic.gdx.graphics.Texture enemyTex;
    private com.badlogic.gdx.graphics.Texture enemy2Tex;
    private com.badlogic.gdx.graphics.Texture enemy3Tex;
    private TiledMapTileLayer groundLayer;
    private java.util.Random rnd;

    private float baseSpawnInterval;
    private float minSpawnInterval;
    private int lastSpawnTick = 0;

    public SpawnManager(EntityManager entityManager,
                        com.badlogic.gdx.graphics.Texture enemyTex,
                        com.badlogic.gdx.graphics.Texture enemy2Tex,
                        com.badlogic.gdx.graphics.Texture enemy3Tex,
                        java.util.Random rnd,
                        float baseSpawnInterval,
                        float minSpawnInterval) {
        this.entityManager     = entityManager;
        this.enemyTex          = enemyTex;
        this.enemy2Tex         = enemy2Tex;
        this.enemy3Tex         = enemy3Tex;
        this.groundLayer       = null;
        this.rnd               = rnd;
        this.baseSpawnInterval = baseSpawnInterval;
        this.minSpawnInterval  = minSpawnInterval;
    }

    public void handleEnemySpawn(int currentTick, int score, GameTickManager tickManager) {
        float currentSpawnInterval = Math.max(minSpawnInterval, baseSpawnInterval - (score * 0.01f));
        int spawnIntervalTicks = (int)(currentSpawnInterval * 20);
        if (tickManager.hasTicksPassed(lastSpawnTick, spawnIntervalTicks)) {
            spawnEnemy();
            lastSpawnTick = currentTick;
        }
    }

    public void setGroundLayer(TiledMapTileLayer groundLayer) {
        this.groundLayer = groundLayer;
    }
    private void spawnEnemy() {
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;
        float spawnX, spawnY;
        int side = rnd.nextInt(4);
        switch (side) {
            case 0:  spawnX = -50;           spawnY = rnd.nextFloat() * mapHeight; break;
            case 1:  spawnX = mapWidth + 50; spawnY = rnd.nextFloat() * mapHeight; break;
            case 2:  spawnX = rnd.nextFloat() * mapWidth; spawnY = mapHeight + 50; break;
            default: spawnX = rnd.nextFloat() * mapWidth; spawnY = -50;            break;
        }
        int type = rnd.nextInt(10);
        if (type < 4)      entityManager.add(new Enemy(spawnX, spawnY, enemyTex));
        else if (type < 8) entityManager.add(new Enemy2(spawnX, spawnY, enemy2Tex));
        else               entityManager.add(new Enemy3(spawnX, spawnY, enemy3Tex));
    }
}