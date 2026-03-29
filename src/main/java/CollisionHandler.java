import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.audio.Sound;

public class CollisionHandler {
    private EntityManager entityManager;
    private Array<Bullet> bullets;
    private Array<BloodParticle> bloods;
    private Player player;
    private Sound popSound;
    private Sound tinSound;
    private Sound splatSound;
    private CollisionListener listener;

    public interface CollisionListener {
        void onEnemyKilled(Entity e);
        void onPlayerDamaged();
        void onPlayerSlowed(BloodParticle b);
    }

    public CollisionHandler(EntityManager entityManager, Array<Bullet> bullets,
                            Array<BloodParticle> bloods, Player player,
                            Sound popSound, Sound tinSound, Sound splatSound,
                            CollisionListener listener) {
        this.entityManager = entityManager;
        this.bullets       = bullets;
        this.bloods        = bloods;
        this.player        = player;
        this.popSound      = popSound;
        this.tinSound      = tinSound;
        this.splatSound    = splatSound;
        this.listener      = listener;
    }

    public void handleAll(boolean hitCooldownRunning) {
        handleBulletEnemy();
        handlePlayerEnemy(hitCooldownRunning);
        handlePlayerBlood(hitCooldownRunning);
    }

    private void handleBulletEnemy() {
        for (Entity e : entityManager.getAll()) {
            for (Bullet b : bullets) {
                if (b.dead) continue;
                if (!e.isDead() && checkBulletCollision(e.getX(), e.getY(), b.x, b.y)) {
                    int damage = resolveDamage(e, b);
                    e.setHp(e.getHp() - damage);
                    if (e.getHp() <= 0) {
                        e.setDead(true);
                        listener.onEnemyKilled(e);
                    }
                }
            }
        }
    }

    private void handlePlayerEnemy(boolean hitCooldownRunning) {
        if (player.dead || hitCooldownRunning) return;
        for (Entity e : entityManager.getAll()) {
            if (!e.isDead() && checkPlayerCollision(e.getX(), e.getY())) {
                listener.onPlayerDamaged();
                return;
            }
        }
    }

    private void handlePlayerBlood(boolean hitCooldownRunning) {
        if (player.dead || hitCooldownRunning) return;
        for (BloodParticle b : bloods) {
            if (b.dead) continue;
            float dist = Vector2.dst(b.x + 4, b.y + 4, player.getCenterX(), player.getCenterY());
            if (dist < 36f) {
                b.dead = true;
                listener.onPlayerSlowed(b);
            }
        }
    }

    private boolean checkBulletCollision(float ex, float ey, float bx, float by) {
        float dist = Vector2.dst(ex + 32, ey + 32, bx + 4, by + 4);
        return dist < 36f;
    }

    private boolean checkPlayerCollision(float ex, float ey) {
        float dist = Vector2.dst(ex + 32, ey + 32, player.getCenterX(), player.getCenterY());
        return dist < 64f;
    }

    private int resolveDamage(Entity e, Bullet b) {
        if (e instanceof Enemy) {
            switch (b.getBulletType()) {
                case AMMO_SMG:    splatSound.play(); return 15;
                case AMMO_PISTOL: tinSound.play(1f); b.dead = true; return 3;
                case AMMO:        tinSound.play(1f); b.dead = true; return 2;
            }
        } else if (e instanceof Enemy2) {
            switch (b.getBulletType()) {
                case AMMO:        splatSound.play(); return 30;
                case AMMO_SMG:    tinSound.play(1f); popSound.play(0.2f); b.dead = true; return 5;
                case AMMO_PISTOL: tinSound.play(1f); popSound.play(0.2f); b.dead = true; return 9;
            }
        } else if (e instanceof Enemy3) {
            switch (b.getBulletType()) {
                case AMMO_PISTOL: splatSound.play(); return 8;
                case AMMO_SMG:    tinSound.play(1f); b.dead = true; return 2;
                case AMMO:        tinSound.play(1f); b.dead = true; return 1;
            }
        }
        return 1;
    }
}