package server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import shared.*;

import java.io.IOException;
import java.util.*;

public class GameServer {

    // --- Sabitler ---
    private static final float MAP_WIDTH  = 96  * 16 * 3f; // flape.tmx boyutuna göre ayarla
    private static final float MAP_HEIGHT = 96  * 16 * 3f;
    private static final float TICK_RATE  = 1f / 20f;      // 20 tick/s
    private static final float BAYONET_RANGE   = 150f;
    private static final float BULLET_SPEED    = 800f;

    // --- Network ---
    private Server server;

    // --- Oyuncu state ---
    private final ServerPlayerState[] players = new ServerPlayerState[2];
    private int connectedCount = 0;

    // --- Entity / spawn ---
    private ServerEntityManager entityManager = new ServerEntityManager();
    private ServerSpawnManager  spawnManager;
    private int score = 0;

    // --- Bullet ---
    private List<ServerBullet> bullets   = new ArrayList<>();
    private int nextBulletId = 0;

    // --- Tick ---
    private int  currentTick = 0;
    private long lastTime;

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    public void start() throws IOException {
        server = new Server(65536, 65536);
        NetworkRegistry.register(server);

        server.addListener(new Listener() {
            @Override
            public void connected(Connection c) {
                if (connectedCount >= 2) { c.close(); return; }
                int pid = connectedCount++;
                players[pid] = new ServerPlayerState(pid, c.getID());
                System.out.println("Oyuncu " + pid + " bağlandı. connID=" + c.getID());

                JoinAckMessage ack = new JoinAckMessage();
                ack.assignedPlayerId = pid;
                ack.gameReady        = (connectedCount == 2);
                server.sendToTCP(c.getID(), ack);

                // İkinci oyuncu bağlandığında birinciye de gameReady gönder
                if (connectedCount == 2) {
                    JoinAckMessage ack0 = new JoinAckMessage();
                    ack0.assignedPlayerId = 0;
                    ack0.gameReady        = true;
                    server.sendToTCP(players[0].connectionId, ack0);
                }
            }

            @Override
            public void disconnected(Connection c) {
                for (ServerPlayerState p : players) {
                    if (p != null && p.connectionId == c.getID()) {
                        p.dead = true;
                        System.out.println("Oyuncu " + p.playerId + " ayrıldı.");
                    }
                }
            }

            @Override
            public void received(Connection c, Object obj) {
                if (obj instanceof PlayerInput) {
                    handleInput((PlayerInput) obj);
                }
            }
        });

        server.bind(NetworkRegistry.TCP_PORT);
        server.start();
        System.out.println("Sunucu başladı. Port: " + NetworkRegistry.TCP_PORT);

        // Map boyutunu bildiğimiz için spawn manager'ı burada başlat
        spawnManager = new ServerSpawnManager(
                entityManager, MAP_WIDTH, MAP_HEIGHT,
                new Random(), 1.2f, 0.6f
        );

        lastTime = System.nanoTime();
        gameLoop();
    }

    // ---------------------------------------------------------------
    // Game Loop — sabit 20 tick/s
    // ---------------------------------------------------------------
    private void gameLoop() {
        while (true) {
            long now  = System.nanoTime();
            float dt  = (now - lastTime) / 1_000_000_000f;
            lastTime  = now;

            if (connectedCount == 2) {
                tick(dt);
            }

            // Bir sonraki tick'e kadar bekle
            long elapsed = System.nanoTime() - now;
            long sleep   = (long)(TICK_RATE * 1_000_000_000L) - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000)); }
                catch (InterruptedException ignored) {}
            }
        }
    }

    // ---------------------------------------------------------------
    // Tek tick
    // ---------------------------------------------------------------
    private void tick(float dt) {
        currentTick++;

        // Enemy spawn
        spawnManager.tick(currentTick, score);

        // Oyuncu pozisyonlarını inputtan güncelle
        for (ServerPlayerState p : players) {
            if (p == null || p.dead) continue;
            applyInput(p, dt);
        }

        // Enemy AI — her enemy en yakın oyuncuya gitsin
        ServerPlayerState p0 = players[0];
        ServerPlayerState p1 = players[1];
        if (p0 != null && p1 != null) {
            entityManager.updateAll(dt,
                    p0.x, p0.y,
                    p1.x, p1.y);
        }

        // Bullet hareket
        updateBullets(dt);

        // Collision
        handleBulletEnemyCollision();
        handlePlayerEnemyCollision();

        // Temizlik
        entityManager.cleanup();
        bullets.removeIf(b -> b.dead);

        // State broadcast
        broadcastGameState();
    }

    // ---------------------------------------------------------------
    // Input uygula → oyuncu pozisyonu güncelle
    // ---------------------------------------------------------------
    private void handleInput(PlayerInput input) {
        ServerPlayerState p = getPlayer(input.playerId);
        if (p == null) return;
        p.lastInput = input;

        // Silah değişimi
        if (input.weaponSlot > 0) p.weaponSlot = input.weaponSlot;

        // Bayonet
        if (input.bayonetPressed && !p.bayonetCooldown) {
            p.bayonetCooldown = true;
            handleBayonet(p);
            // Cooldown sıfırla (basit: N tick sonra false yap)
            final ServerPlayerState fp = p;
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                fp.bayonetCooldown = false;
            }).start();
        }

        // Ateş
        if ((input.fireKeyboard || input.fireTrigger) && !p.shootCooldown) {
            spawnBullet(p, input.aimAngle);
            p.shootCooldown = true;
            final ServerPlayerState fp = p;
            new Thread(() -> {
                try { Thread.sleep(getShootCooldownMs(fp.weaponSlot)); }
                catch (InterruptedException ignored) {}
                fp.shootCooldown = false;
            }).start();
        }
    }

    private void applyInput(ServerPlayerState p, float dt) {
        if (p.lastInput == null) return;
        PlayerInput in = p.lastInput;
        float speed = 1000f;

        float mx = 0, my = 0;
        if (in.up)    my += speed * dt;
        if (in.down)  my -= speed * dt;
        if (in.left)  mx -= speed * dt;
        if (in.right) mx += speed * dt;

        // Gamepad eksen
        if (Math.abs(in.gamepadMoveX) > 0.2f) mx += in.gamepadMoveX * speed * dt;
        if (Math.abs(in.gamepadMoveY) > 0.2f) my += in.gamepadMoveY * speed * dt;

        p.x = clamp(p.x + mx, 0, MAP_WIDTH  - 64);
        p.y = clamp(p.y + my, 0, MAP_HEIGHT - 64);
    }

    // ---------------------------------------------------------------
    // Bullet
    // ---------------------------------------------------------------
    private void spawnBullet(ServerPlayerState p, float angle) {
        int count = 1;
        float spread = 0;
        int type = p.weaponSlot;

        if (type == 2) { count = 6; spread = 15f; } // shotgun

        double rad = Math.toRadians(angle);
        for (int i = 0; i < count; i++) {
            double a = rad + Math.toRadians((i - count / 2f) * spread);
            ServerBullet b = new ServerBullet();
            b.id   = nextBulletId++;
            b.x    = p.x + 32;
            b.y    = p.y + 32;
            b.vx   = (float)(Math.cos(a) * BULLET_SPEED);
            b.vy   = (float)(Math.sin(a) * BULLET_SPEED);
            b.type = (byte) type;
            bullets.add(b);
        }
    }

    private void updateBullets(float dt) {
        for (ServerBullet b : bullets) {
            if (b.dead) continue;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            // Map dışına çıktıysa öldür
            if (b.x < 0 || b.x > MAP_WIDTH || b.y < 0 || b.y > MAP_HEIGHT) b.dead = true;
            b.lifetime += dt;
            if (b.lifetime > 3f) b.dead = true;
        }
    }

    // ---------------------------------------------------------------
    // Collision
    // ---------------------------------------------------------------
    private void handleBulletEnemyCollision() {
        for (ServerEntity e : entityManager.getAll()) {
            if (e.dead) continue;
            for (ServerBullet b : bullets) {
                if (b.dead) continue;
                float dist = dist(e.x + 32, e.y + 32, b.x, b.y);
                if (dist < 36f) {
                    int dmg = resolveDamage(e.type, b.type);
                    e.hp -= dmg;
                    if (e.hp <= 0) { e.dead = true; score++; }
                    if (killsBullet(e.type, b.type)) b.dead = true;
                }
            }
        }
    }

    private void handlePlayerEnemyCollision() {
        for (ServerPlayerState p : players) {
            if (p == null || p.dead || p.hitCooldown) continue;
            for (ServerEntity e : entityManager.getAll()) {
                if (e.dead) continue;
                float dist = dist(e.x + 32, e.y + 32, p.x + 32, p.y + 32);
                if (dist < 64f) {
                    p.hp--;
                    p.hitCooldown = true;
                    if (p.hp <= 0) p.dead = true;
                    final ServerPlayerState fp = p;
                    new Thread(() -> {
                        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                        fp.hitCooldown = false;
                    }).start();
                    break;
                }
            }
        }
    }

    private void handleBayonet(ServerPlayerState p) {
        int killed = 0;
        for (ServerEntity e : entityManager.getAll()) {
            if (e.dead) continue;
            if (dist(e.x + 32, e.y + 32, p.x + 32, p.y + 32) < BAYONET_RANGE) {
                e.dead = true;
                score++;
                killed++;
            }
        }
        // Regen kalp
        if (killed >= 3 && p.hp < 3) { p.hp = Math.min(3, p.hp + 2); }
        else if (killed == 2 && p.hp < 3) { p.hp++; }
    }

    // ---------------------------------------------------------------
    // Broadcast
    // ---------------------------------------------------------------
    private void broadcastGameState() {
        GameState state = new GameState();
        state.tick  = currentTick;
        state.score = score;

        for (ServerPlayerState p : players) {
            if (p == null) continue;
            PlayerSnapshot ps = new PlayerSnapshot();
            ps.playerId   = p.playerId;
            ps.x          = p.x;
            ps.y          = p.y;
            ps.hp         = p.hp;
            ps.dead       = p.dead;
            ps.weaponSlot = p.weaponSlot;
            state.players.add(ps);
        }

        for (ServerEntity e : entityManager.getAll()) {
            EntitySnapshot es = new EntitySnapshot();
            es.id   = e.id;
            es.type = e.type;
            es.x    = e.x;
            es.y    = e.y;
            es.hp   = e.hp;
            es.dead = e.dead;
            state.entities.add(es);
        }

        for (ServerBullet b : bullets) {
            BulletSnapshot bs = new BulletSnapshot();
            bs.id         = b.id;
            bs.x          = b.x;
            bs.y          = b.y;
            bs.bulletType = b.type;
            bs.dead       = b.dead;
            state.bullets.add(bs);
        }

        server.sendToAllTCP(state);
    }

    // ---------------------------------------------------------------
    // Yardımcı
    // ---------------------------------------------------------------
    private int resolveDamage(byte entityType, byte bulletType) {
        // CollisionHandler'daki mantığın aynısı
        if (entityType == EntitySnapshot.TYPE_ENEMY) {
            if (bulletType == 2) return 15; // SMG
            if (bulletType == 0) return 3;  // Pistol
            if (bulletType == 1) return 2;  // Shotgun
        } else if (entityType == EntitySnapshot.TYPE_ENEMY2) {
            if (bulletType == 1) return 30;
            if (bulletType == 2) return 5;
            if (bulletType == 0) return 9;
        } else if (entityType == EntitySnapshot.TYPE_ENEMY3) {
            if (bulletType == 0) return 8;
            if (bulletType == 2) return 2;
            if (bulletType == 1) return 1;
        }
        return 1;
    }

    private boolean killsBullet(byte entityType, byte bulletType) {
        if (entityType == EntitySnapshot.TYPE_ENEMY)  return bulletType == 0 || bulletType == 1;
        if (entityType == EntitySnapshot.TYPE_ENEMY2) return bulletType == 2 || bulletType == 0;
        if (entityType == EntitySnapshot.TYPE_ENEMY3) return bulletType == 2 || bulletType == 1;
        return false;
    }

    private long getShootCooldownMs(int weaponSlot) {
        if (weaponSlot == 2) return 100; // SMG hızlı
        if (weaponSlot == 1) return 500; // Shotgun yavaş
        return 400; // Pistol
    }

    private ServerPlayerState getPlayer(int pid) {
        for (ServerPlayerState p : players) if (p != null && p.playerId == pid) return p;
        return null;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------
    // İç sınıflar
    // ---------------------------------------------------------------
    static class ServerPlayerState {
        int playerId, connectionId;
        float x = 2036, y = 1951;
        int hp = 3;
        boolean dead = false;
        int weaponSlot = 1;
        boolean shootCooldown = false;
        boolean hitCooldown   = false;
        boolean bayonetCooldown = false;
        PlayerInput lastInput = null;

        ServerPlayerState(int pid, int cid) {
            this.playerId     = pid;
            this.connectionId = cid;
        }
    }

    static class ServerBullet {
        int id;
        float x, y, vx, vy;
        float lifetime = 0;
        byte type;
        boolean dead = false;
    }
}