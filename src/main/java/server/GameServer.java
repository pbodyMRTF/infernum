package server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import server.*;
import shared.*;

import java.io.IOException;
import java.util.*;

public class GameServer {

    private static final float PLAYER_SIZE     = 64f; // player64.png varsayımı — DOĞRULAYIN
    private static final float BAYONET_RANGE   = 150f;
    private static final float PLAYER_SPEED    = 1000f;

    private static final int SHOOT_COOLDOWN_DEFAULT_TICKS   = 16;
    private static final int HIT_COOLDOWN_TICKS              = 16;
    private static final int BAYONET_COOLDOWN_TICKS          = 60;

    private Server server;
    private CollisionGrid collisionGrid;
    private float mapWidth, mapHeight;

    private final ServerPlayerState[] players = new ServerPlayerState[2];
    private int connectedCount = 0;

    private ServerEntityManager entityManager = new ServerEntityManager();
    private ServerSpawnManager spawnManager;
    private int score = 0;

    private List<ServerBullet> bullets = new ArrayList<>();
    private int nextBulletId = 0;
    private int nextEntityId = 0; // ServerSpawnManager içinde yönetiliyor zaten

    private int  currentTick = 0;
    private long lastTime;

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    public void start() throws IOException {
        collisionGrid = CollisionGrid.loadFromFile("collision_flape.txt");
        mapWidth  = collisionGrid.getMapWidthPixels();
        mapHeight = collisionGrid.getMapHeightPixels();
        System.out.println("Harita boyutu: " + mapWidth + "x" + mapHeight);

        server = new Server(65536, 65536);
        NetworkRegistry.register(server);

        server.addListener(new Listener() {
            @Override
            public void connected(Connection c) {
                int pid = -1;
                for (int i = 0; i < players.length; i++) {
                    if (players[i] == null) { pid = i; break; }
                }
                if (pid == -1) { c.close(); return; }

                players[pid] = new ServerPlayerState(pid, c.getID());
                connectedCount++;
                System.out.println("Oyuncu " + pid + " bağlandı. connID=" + c.getID());

                JoinAckMessage ack = new JoinAckMessage();
                ack.assignedPlayerId = pid;
                ack.gameReady        = (connectedCount == 2);
                server.sendToTCP(c.getID(), ack);

                if (connectedCount == 2) {
                    JoinAckMessage ack0 = new JoinAckMessage();
                    ack0.assignedPlayerId = 0;
                    ack0.gameReady        = true;
                    server.sendToTCP(players[0].connectionId, ack0);
                }
            }

            @Override
            public void disconnected(Connection c) {
                for (int i = 0; i < players.length; i++) {
                    ServerPlayerState p = players[i];
                    if (p != null && p.connectionId == c.getID()) {
                        System.out.println("Oyuncu " + p.playerId + " ayrıldı.");
                        players[i] = null;
                        connectedCount--;
                    }
                }
            }

            @Override
            public void received(Connection c, Object obj) {
                if (obj instanceof PlayerInput) handleInput((PlayerInput) obj);
            }
        });

        server.bind(NetworkRegistry.TCP_PORT);
        server.start();
        System.out.println("Sunucu başladı. Port: " + NetworkRegistry.TCP_PORT);

        spawnManager = new ServerSpawnManager(
                entityManager, mapWidth, mapHeight,
                new Random(), 1.2f, 0.6f
        );

        lastTime = System.nanoTime();
        gameLoop();
    }

    private void gameLoop() {
        while (true) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            if (connectedCount == 2) tick(dt);

            long elapsed = System.nanoTime() - now;
            long sleep   = (long)(ServerTickTimer.TICK_RATE * 1_000_000_000L) - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000)); }
                catch (InterruptedException ignored) {}
            }
        }
    }

    private void tick(float dt) {
        currentTick++;

        spawnManager.tick(currentTick, score);

        for (ServerPlayerState p : players) {
            if (p == null || p.dead) continue;
            applyMovement(p, dt);
            processCooldowns(p);
        }

        ServerPlayerState p0 = players[0];
        ServerPlayerState p1 = players[1];
        if (p0 != null && p1 != null) {
            List<float[]> aliveTargets = new ArrayList<>();
            if (!p0.dead) aliveTargets.add(new float[]{p0.x, p0.y});
            if (!p1.dead) aliveTargets.add(new float[]{p1.x, p1.y});
            entityManager.updateAll(dt, aliveTargets);
        }

        updateBullets(dt);
        handleBulletEnemyCollision();
        handlePlayerEnemyCollision();

        entityManager.cleanup();
        bullets.removeIf(b -> b.dead);

        broadcastGameState();
    }

    // -----------------------------------------------------------
    // Input
    // -----------------------------------------------------------
    private void handleInput(PlayerInput input) {
        ServerPlayerState p = getPlayer(input.playerId);
        if (p == null || p.dead) return;
        p.lastInput = input;

        if (input.weaponSlot > 0 && input.weaponSlot != p.weaponSlot) {
            p.weaponSlot = input.weaponSlot;
            p.shootCooldown.stop(); // GameScreen'deki weaponJustChanged davranışı
        }

        if (input.bayonetPressed && !p.bayonetCooldown.isRunning()) {
            p.bayonetCooldown.start(currentTick);
            handleBayonet(p);
        }

        WeaponStats w = WeaponStats.forSlot(p.weaponSlot);
        boolean fireHeld = input.fireKeyboard || input.fireTrigger;

        boolean shouldFire;
        if (w.automatic) {
            shouldFire = fireHeld;
        } else {
            // yarı otomatik: sadece basma anında (edge detection)
            shouldFire = fireHeld && !p.prevFireHeld;
        }
        p.prevFireHeld = fireHeld;

        if (shouldFire && !p.shootCooldown.isRunning()) {
            spawnBullets(p, input.aimAngle, w);
            p.shootCooldown = new ServerTickTimer(w.fireRateTicks);
            p.shootCooldown.start(currentTick);
        }
    }

    private void applyMovement(ServerPlayerState p, float dt) {
        if (p.lastInput == null) return;
        PlayerInput in = p.lastInput;

        float mx = 0, my = 0;
        if (in.up)    my += PLAYER_SPEED * dt;
        if (in.down)  my -= PLAYER_SPEED * dt;
        if (in.left)  mx -= PLAYER_SPEED * dt;
        if (in.right) mx += PLAYER_SPEED * dt;

        if (Math.abs(in.gamepadMoveX) > 0.2f) mx += in.gamepadMoveX * PLAYER_SPEED * dt;
        if (Math.abs(in.gamepadMoveY) > 0.2f) my -= in.gamepadMoveY * PLAYER_SPEED * dt;

        float nextX = p.x + mx;
        float nextY = p.y + my;

        if (!isPlayerBlocked(nextX, p.y)) p.x = nextX;
        if (!isPlayerBlocked(p.x, nextY)) p.y = nextY;

        p.x = clamp(p.x, 0, mapWidth  - PLAYER_SIZE);
        p.y = clamp(p.y, 0, mapHeight - PLAYER_SIZE);
    }

    // Orijinal Player.isBlocked'daki 8 nokta kontrolü
    private boolean isPlayerBlocked(float x, float y) {
        float[][] points = {
                {x, y}, {x + PLAYER_SIZE, y},
                {x, y + PLAYER_SIZE}, {x + PLAYER_SIZE, y + PLAYER_SIZE},
                {x + PLAYER_SIZE / 2, y}, {x + PLAYER_SIZE / 2, y + PLAYER_SIZE},
                {x, y + PLAYER_SIZE / 2}, {x + PLAYER_SIZE, y + PLAYER_SIZE / 2}
        };
        for (float[] pt : points) {
            if (collisionGrid.isBlockedWorld(pt[0], pt[1])) return true;
        }
        return false;
    }

    private void processCooldowns(ServerPlayerState p) {
        if (p.shootCooldown.isFinished(currentTick))   p.shootCooldown.stop();
        if (p.hitCooldown.isFinished(currentTick))     p.hitCooldown.stop();
        if (p.bayonetCooldown.isFinished(currentTick)) p.bayonetCooldown.stop();
    }

    // -----------------------------------------------------------
    // Bullet
    // -----------------------------------------------------------
    private void spawnBullets(ServerPlayerState p, float angle, WeaponStats w) {
        double rad = Math.toRadians(angle);
        for (int i = 0; i < w.bulletCount; i++) {
            float spread = w.bulletSpread > 0
                    ? (float)((Math.random() * 2 - 1) * w.bulletSpread)
                    : 0f;
            double a = rad + Math.toRadians(spread);

            ServerBullet b = new ServerBullet();
            b.id    = nextBulletId++;
            b.x     = p.x + PLAYER_SIZE / 2 - 4; // size/2 offset (Bullet.java: x - size/2)
            b.y     = p.y + PLAYER_SIZE / 2 - 4;
            b.vx    = (float)(Math.cos(a) * w.bulletSpeed);
            b.vy    = (float)(Math.sin(a) * w.bulletSpeed);
            b.type  = w.bulletType;
            bullets.add(b);
        }
    }

    private void updateBullets(float dt) {
        float size = 8f;
        for (ServerBullet b : bullets) {
            if (b.dead) continue;

            float oldX = b.x, oldY = b.y;
            b.x += b.vx * dt;
            b.y += b.vy * dt;

            if (collisionGrid.isBlockedWorld(b.x, b.y)) {
                int oldTileX = (int)(oldX / (collisionGrid.tileWidth * 3f));
                int newTileX = (int)(b.x   / (collisionGrid.tileWidth * 3f));
                if (oldTileX != newTileX) { b.vx = -b.vx; b.x = oldX; }
                else                      { b.vy = -b.vy; b.y = oldY; }
                b.bounceCount++;
            }

            if (b.x <= 0 || b.x >= mapWidth - size)  { b.vx = -b.vx; b.bounceCount++; }
            if (b.y <= 0 || b.y >= mapHeight - size) { b.vy = -b.vy; b.bounceCount++; }

            if (b.bounceCount > 5) b.dead = true;
        }
    }

    // -----------------------------------------------------------
    // Collision — orijinal CollisionHandler.resolveDamage BİREBİR
    // -----------------------------------------------------------
    private void handleBulletEnemyCollision() {
        for (ServerEntity e : entityManager.getAll()) {
            if (e.dead) continue;
            for (ServerBullet b : bullets) {
                if (b.dead) continue;
                float dist = dist(e.x + 32, e.y + 32, b.x + 4, b.y + 4);
                if (dist < 36f) {
                    int dmg = resolveDamage(e.type, b.type);
                    e.hp -= dmg;
                    if (killsBullet(e.type, b.type)) b.dead = true;
                    if (e.hp <= 0) { e.dead = true; score++; }
                }
            }
        }
    }

    private int resolveDamage(byte entityType, byte bulletType) {
        if (entityType == EntitySnapshot.TYPE_ENEMY) {
            if (bulletType == WeaponStats.BULLET_AMMO_SMG)    return 15;
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 3;
            if (bulletType == WeaponStats.BULLET_AMMO)        return 2;
        } else if (entityType == EntitySnapshot.TYPE_ENEMY2) {
            if (bulletType == WeaponStats.BULLET_AMMO)        return 30;
            if (bulletType == WeaponStats.BULLET_AMMO_SMG)    return 5;
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 9;
        } else if (entityType == EntitySnapshot.TYPE_ENEMY3) {
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 8;
            if (bulletType == WeaponStats.BULLET_AMMO_SMG)    return 2;
            if (bulletType == WeaponStats.BULLET_AMMO)        return 1;
        }
        return 1;
    }

    // Orijinalde: Enemy'de sadece AMMO_SMG mermiyi öldürmüyor (deler), diğerleri dead=true
    private boolean killsBullet(byte entityType, byte bulletType) {
        if (entityType == EntitySnapshot.TYPE_ENEMY)
            return bulletType != WeaponStats.BULLET_AMMO_SMG;
        if (entityType == EntitySnapshot.TYPE_ENEMY2)
            return bulletType != WeaponStats.BULLET_AMMO; // AMMO(shotgun) deler
        if (entityType == EntitySnapshot.TYPE_ENEMY3)
            return bulletType != WeaponStats.BULLET_AMMO_PISTOL; // pistol deler
        return true;
    }

    private void handlePlayerEnemyCollision() {
        for (ServerPlayerState p : players) {
            if (p == null || p.dead || p.hitCooldown.isRunning()) continue;
            for (ServerEntity e : entityManager.getAll()) {
                if (e.dead) continue;
                float dist = dist(e.x + 32, e.y + 32, p.x + PLAYER_SIZE/2, p.y + PLAYER_SIZE/2);
                if (dist < 64f) {
                    p.hp--;
                    p.hitCooldown.start(currentTick);
                    if (p.hp <= 0) p.dead = true;
                    break;
                }
            }
        }
    }

    private void handleBayonet(ServerPlayerState p) {
        int killed = 0;
        for (ServerEntity e : entityManager.getAll()) {
            if (e.dead) continue;
            if (dist(e.x + 32, e.y + 32, p.x + PLAYER_SIZE/2, p.y + PLAYER_SIZE/2) < BAYONET_RANGE) {
                e.dead = true;
                score++;
                killed++;
            }
        }
        if (killed >= 3 && p.hp < 3)      p.hp = Math.min(3, p.hp + 2);
        else if (killed == 2 && p.hp < 3) p.hp++;
    }

    // -----------------------------------------------------------
    // Broadcast
    // -----------------------------------------------------------
    private void broadcastGameState() {
        GameState state = new GameState();
        state.tick  = currentTick;
        state.score = score;

        for (ServerPlayerState p : players) {
            if (p == null) continue;
            PlayerSnapshot ps = new PlayerSnapshot();
            ps.playerId   = p.playerId;
            ps.x = p.x; ps.y = p.y;
            ps.hp = p.hp; ps.dead = p.dead;
            ps.weaponSlot = p.weaponSlot;
            ps.aimAngle = p.lastInput != null ? p.lastInput.aimAngle : 0f;
            state.players.add(ps);
        }
        for (ServerEntity e : entityManager.getAll()) {
            EntitySnapshot es = new EntitySnapshot();
            es.id    = e.id;
            es.type  = e.type;
            es.x     = e.x;
            es.y     = e.y;
            es.hp    = e.hp;
            es.maxHp = e.maxHp;
            es.dead  = e.dead;
            state.entities.add(es);
        }
        for (ServerBullet b : bullets) {
            BulletSnapshot bs = new BulletSnapshot();
            bs.id = b.id; bs.x = b.x; bs.y = b.y;
            bs.bulletType = b.type; bs.dead = b.dead;
            state.bullets.add(bs);
        }
        server.sendToAllTCP(state);
    }

    // -----------------------------------------------------------
    private ServerPlayerState getPlayer(int pid) {
        for (ServerPlayerState p : players) if (p != null && p.playerId == pid) return p;
        return null;
    }
    private float dist(float x1,float y1,float x2,float y2){float dx=x2-x1,dy=y2-y1;return (float)Math.sqrt(dx*dx+dy*dy);}
    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}

    static class ServerPlayerState {
        int playerId, connectionId;
        float x = 2036, y = 1951;
        int hp = 3;
        boolean dead = false;
        int weaponSlot = 1;
        boolean prevFireHeld = false;
        ServerTickTimer shootCooldown   = new ServerTickTimer(SHOOT_COOLDOWN_DEFAULT_TICKS);
        ServerTickTimer hitCooldown     = new ServerTickTimer(HIT_COOLDOWN_TICKS);
        ServerTickTimer bayonetCooldown = new ServerTickTimer(BAYONET_COOLDOWN_TICKS);
        PlayerInput lastInput = null;

        ServerPlayerState(int pid, int cid) { this.playerId = pid; this.connectionId = cid; }
    }

    static class ServerBullet {
        int id;
        float x, y, vx, vy;
        int bounceCount = 0;
        byte type;
        boolean dead = false;
    }
}