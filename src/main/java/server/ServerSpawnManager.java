package server;

import shared.EntitySnapshot;
import java.util.Random;

public class ServerSpawnManager {
    private ServerEntityManager entityManager;
    private float mapWidth, mapHeight;
    private Random rnd;
    private float baseSpawnInterval;
    private float minSpawnInterval;
    private int lastSpawnTick = 0;
    private int nextEntityId  = 0;

    public ServerSpawnManager(ServerEntityManager entityManager,
                              float mapWidth, float mapHeight,
                              Random rnd,
                              float baseSpawnInterval,
                              float minSpawnInterval) {
        this.entityManager     = entityManager;
        this.mapWidth          = mapWidth;
        this.mapHeight         = mapHeight;
        this.rnd               = rnd;
        this.baseSpawnInterval = baseSpawnInterval;
        this.minSpawnInterval  = minSpawnInterval;
    }

    public void tick(int currentTick, int score) {
        float interval = Math.max(minSpawnInterval, baseSpawnInterval - score * 0.01f);
        int intervalTicks = (int)(interval * 20);
        if (currentTick - lastSpawnTick >= intervalTicks) {
            spawnEnemy();
            lastSpawnTick = currentTick;
        }
    }

    private void spawnEnemy() {
        float sx, sy;
        int side = rnd.nextInt(4);
        switch (side) {
            case 0:  sx = -50;            sy = rnd.nextFloat() * mapHeight; break;
            case 1:  sx = mapWidth + 50;  sy = rnd.nextFloat() * mapHeight; break;
            case 2:  sx = rnd.nextFloat() * mapWidth; sy = mapHeight + 50;  break;
            default: sx = rnd.nextFloat() * mapWidth; sy = -50;             break;
        }

        int roll = rnd.nextInt(10);
        byte type;
        float speed;
        int hp;

        if (roll < 4) {
            type  = EntitySnapshot.TYPE_ENEMY;
            speed = 200 + rnd.nextFloat() * 80;
            hp    = 15;
        } else if (roll < 8) {
            type  = EntitySnapshot.TYPE_ENEMY2;
            speed = 100 + rnd.nextFloat() * 70;
            hp    = 30;
        } else {
            type  = EntitySnapshot.TYPE_ENEMY3;
            speed = 256 + rnd.nextFloat() * 128;
            hp    = 8;
        }

        entityManager.add(new ServerEntity(nextEntityId++, type, sx, sy, speed, hp));
    }
}