import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Array;

public class ShootingHandler {
    private Player player;
    private Array<Bullet> bullets;
    private Sound shootSound;
    private Sound smgSound;
    private Sound shotgunSound;
    private OrthographicCamera camera;
    private ShootingListener listener;

    public interface ShootingListener {
        void onShoot(GameTickManager.TickTimer newCooldown);
    }

    public ShootingHandler(Player player, Array<Bullet> bullets,
                           Sound shootSound, Sound smgSound, Sound shotgunSound,
                           OrthographicCamera camera, ShootingListener listener) {
        this.player       = player;
        this.bullets      = bullets;
        this.shootSound   = shootSound;
        this.smgSound     = smgSound;
        this.shotgunSound = shotgunSound;
        this.camera       = camera;
        this.listener     = listener;
    }

    public void handle(boolean shootCooldownRunning, int currentTick) {
        if (player.dead) return;
        Weapons w = player.getWeapon();
        if (w == null) return;

        boolean triggerNow = player.isTriggerPressed();
        boolean shootInput;

        if (w.isAutomatic()) {
            shootInput = Gdx.input.isButtonPressed(Input.Buttons.LEFT) || triggerNow;
        } else {
            shootInput = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                    || (triggerNow && !player.prevTriggerFired);
        }

        player.prevTriggerFired = triggerNow;

        if (shootInput && !shootCooldownRunning) {
            float baseAngle = player.getAngleToMouse(camera);

            Bullet.BulletType bulletType = switch (w.getType()) {
                case SHOTGUN -> {
                    shotgunSound.play(0.7f);
                    yield Bullet.BulletType.AMMO;
                }
                case SMG -> {
                    smgSound.play(0.7f);
                    yield Bullet.BulletType.AMMO_SMG;
                }
                default -> {
                    shootSound.play(0.7f);
                    yield Bullet.BulletType.AMMO_PISTOL;
                }
            };

            for (int i = 0; i < w.getBulletCount(); i++) {
                float spread = MathUtils.random(-w.getBulletSpread(), w.getBulletSpread());
                bullets.add(new Bullet(
                        player.getCenterX(),
                        player.getCenterY(),
                        baseAngle + spread,
                        bulletType
                ));
            }

            GameTickManager.TickTimer newCooldown = new GameTickManager.TickTimer(w.getFireRateTicks());
            newCooldown.start(currentTick);
            listener.onShoot(newCooldown);
        }
    }
}