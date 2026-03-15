import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Player {
    private Weapons currentWeapon;
    private boolean weaponJustChanged = false;

    public float x, y;

    private Texture texture;
    private Texture gun;

    public float speed = 1000f;
    private static final float DEFAULT_SPEED = 1000f;

    public boolean dead = false;
    private int hp = 3;
    private static final int MAX_HP = 3;
    private boolean[] regenHearts = new boolean[MAX_HP];

    private GameTickManager.TickTimer hitCooldown;
    private GameTickManager.TickTimer bayonetCooldown;
    private Sound hitSound;

    private static final int GAMEPAD_BUTTON_RIGHT_BUMPER = 5;   // RB  — bayonet
    private static final int GAMEPAD_BUTTON_X            = 2;   // X   — silah 1
    private static final int GAMEPAD_BUTTON_Y            = 3;   // Y   — silah 2
    private static final int GAMEPAD_BUTTON_B            = 1;   // B   — silah 3

    private static final int   GAMEPAD_AXIS_LEFT_X       = 0;   // Sol analog yatay
    private static final int   GAMEPAD_AXIS_LEFT_Y       = 1;   // Sol analog dikey
    private static final int   GAMEPAD_AXIS_RIGHT_X      = 2;   // Sağ analog yatay
    private static final int   GAMEPAD_AXIS_RIGHT_Y      = 3;   // Sağ analog dikey
    private static final int   GAMEPAD_AXIS_RIGHT_TRIGGER = 5;  // RT  — ateş

    private static final float DEADZONE         = 0.2f;
    private static final float TRIGGER_DEADZONE = 0.5f;

    private boolean prevRightBumper  = false;
    private boolean prevButtonX      = false;
    private boolean prevButtonY      = false;
    private boolean prevButtonB      = false;
    public  boolean prevTriggerFired = false;

    private float gamepadAimAngle = 0f;

    public interface BayonetCallback {
        int onBayonetUse();
    }
    private BayonetCallback bayonetCallback;

    public Player(float x, float y, Texture playerTexture, int controlScheme) {
        this.x       = x;
        this.y       = y;
        this.texture = playerTexture;
        this.hitSound = Assets.getSound(Assets.Sounds.WOOD);

        for (int i = 0; i < MAX_HP; i++) regenHearts[i] = false;
    }

    public void setBayonetCallback(BayonetCallback callback) { this.bayonetCallback = callback; }
    public void setBayonetCooldown(GameTickManager.TickTimer cooldown) { this.bayonetCooldown = cooldown; }
    public void setHitCooldown(GameTickManager.TickTimer cooldown)     { this.hitCooldown = cooldown; }

    public void update(float dt, TiledMapTileLayer wallLayer, TiledMapTileLayer lowObstacleLayer) {
        handleMovement(dt, wallLayer, lowObstacleLayer);
        handleSwapping();
    }

    private Controller getGamepad() {
        if (Controllers.getControllers().size > 0) return Controllers.getControllers().first();
        return null;
    }

    private boolean gamepadJustPressed(Controller c, int button, boolean prevState) {
        return c != null && c.getButton(button) && !prevState;
    }

    private void handleSwapping() {
        Controller c = getGamepad();

        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) this.setWeapon(new Weapons(Weapons.WeaponType.PISTOL));
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) this.setWeapon(new Weapons(Weapons.WeaponType.SHOTGUN));
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) this.setWeapon(new Weapons(Weapons.WeaponType.SMG));

        if (gamepadJustPressed(c, GAMEPAD_BUTTON_X, prevButtonX)) this.setWeapon(new Weapons(Weapons.WeaponType.PISTOL));
        if (gamepadJustPressed(c, GAMEPAD_BUTTON_Y, prevButtonY)) this.setWeapon(new Weapons(Weapons.WeaponType.SHOTGUN));
        if (gamepadJustPressed(c, GAMEPAD_BUTTON_B, prevButtonB)) this.setWeapon(new Weapons(Weapons.WeaponType.SMG));

        boolean bayonetTriggered =
                Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) ||
                        gamepadJustPressed(c, GAMEPAD_BUTTON_RIGHT_BUMPER, prevRightBumper);

        if (bayonetTriggered) {
            if (bayonetCooldown != null && !bayonetCooldown.isRunning()) {
                if (bayonetCallback != null) {
                    int killedCount = bayonetCallback.onBayonetUse();

                    if (killedCount >= 3) {
                        int healsGiven = 0;
                        if (hp < MAX_HP) { regenHearts[hp] = true; hp++; healsGiven++; }
                        if (hp < MAX_HP) { regenHearts[hp] = true; hp++; healsGiven++; }
                        if (healsGiven > 0) Assets.getSound(Assets.Sounds.POP).play(1.0f);
                    } else if (killedCount == 2 && hp < MAX_HP) {
                        regenHearts[hp] = true;
                        hp++;
                        Assets.getSound(Assets.Sounds.POP).play(1.0f);
                    }
                }
            }
        }

        if (c != null) {
            prevRightBumper = c.getButton(GAMEPAD_BUTTON_RIGHT_BUMPER);
            prevButtonX     = c.getButton(GAMEPAD_BUTTON_X);
            prevButtonY     = c.getButton(GAMEPAD_BUTTON_Y);
            prevButtonB     = c.getButton(GAMEPAD_BUTTON_B);
        } else {
            prevRightBumper = prevButtonX = prevButtonY = prevButtonB = false;
        }
    }

    private boolean isBlocked(float nextX, float nextY, TiledMapTileLayer wallLayer, TiledMapTileLayer lowObstacleLayer) {
        if (wallLayer == null) return false;
        float unitScale = 3f;
        float tileW = wallLayer.getTileWidth()  * unitScale;
        float tileH = wallLayer.getTileHeight() * unitScale;

        float[][] points = {
                {nextX, nextY},
                {nextX + texture.getWidth(), nextY},
                {nextX, nextY + texture.getHeight()},
                {nextX + texture.getWidth(), nextY + texture.getHeight()},
                {nextX + texture.getWidth() / 2, nextY},
                {nextX + texture.getWidth() / 2, nextY + texture.getHeight()},
                {nextX, nextY + texture.getHeight() / 2},
                {nextX + texture.getWidth(), nextY + texture.getHeight() / 2}
        };

        for (float[] p : points) {
            int tileX = (int) (p[0] / tileW);
            int tileY = (int) (p[1] / tileH);

            if (tileX < 0 || tileY < 0 || tileX >= wallLayer.getWidth() || tileY >= wallLayer.getHeight())
                return true;
            if (wallLayer.getCell(tileX, tileY) != null)
                return true;
            if (lowObstacleLayer != null && lowObstacleLayer.getCell(tileX, tileY) != null)
                return true;
        }
        return false;
    }

    private void handleMovement(float dt, TiledMapTileLayer wallLayer, TiledMapTileLayer lowObstacleLayer) {
        if (dead) return;

        float moveX = 0;
        float moveY = 0;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) moveY += speed * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) moveY -= speed * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) moveX -= speed * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) moveX += speed * dt;

        Controller c = getGamepad();
        if (c != null) {
            float axisX = c.getAxis(GAMEPAD_AXIS_LEFT_X);
            float axisY = c.getAxis(GAMEPAD_AXIS_LEFT_Y);

            if (Math.abs(axisX) > DEADZONE) moveX += axisX * speed * dt;
            if (Math.abs(axisY) > DEADZONE) moveY -= axisY * speed * dt;

            float aimX = c.getAxis(GAMEPAD_AXIS_RIGHT_X);
            float aimY = c.getAxis(GAMEPAD_AXIS_RIGHT_Y);
            if (Math.abs(aimX) > DEADZONE || Math.abs(aimY) > DEADZONE) {
                gamepadAimAngle = (float) Math.toDegrees(Math.atan2(-aimY, aimX));
            }
        }

        if (!isBlocked(x + moveX, y, wallLayer, lowObstacleLayer)) x += moveX;
        if (!isBlocked(x, y + moveY, wallLayer, lowObstacleLayer)) y += moveY;

        if (wallLayer != null) {
            float mapWidth  = wallLayer.getWidth()  * wallLayer.getTileWidth()  * 3f;
            float mapHeight = wallLayer.getHeight() * wallLayer.getTileHeight() * 3f;
            x = MathUtils.clamp(x, 0, mapWidth  - texture.getWidth());
            y = MathUtils.clamp(y, 0, mapHeight - texture.getHeight());
        }
    }

    public boolean isTriggerPressed() {
        Controller c = getGamepad();
        if (c == null) return false;
        return c.getAxis(GAMEPAD_AXIS_RIGHT_TRIGGER) > TRIGGER_DEADZONE;
    }

    public void damage(int amount) {
        if (dead) return;
        if (hitCooldown != null && hitCooldown.isRunning()) return;

        hitSound.play(0.9f);
        hp -= amount;
        if (hp <= 0) {
            hp   = 0;
            dead = true;
        } else {
            if (hp < MAX_HP) regenHearts[hp] = false;
        }
    }

    public void draw(SpriteBatch batch) {
        batch.draw(texture, x, y);
    }

    public void drawGun(SpriteBatch batch, OrthographicCamera camera) {
        if (dead || currentWeapon == null) return;

        float angle = getAngleToMouse(camera);
        float scale = currentWeapon.getGunScale();

        batch.draw(
                gun,
                getCenterX(), getCenterY(),
                0, gun.getHeight() / 2f,
                gun.getWidth(), gun.getHeight(),
                scale, scale,
                angle,
                0, 0, gun.getWidth(), gun.getHeight(),
                false, false
        );
    }

    public float getAngleToMouse(OrthographicCamera camera) {
        Controller c = getGamepad();
        if (c != null) {
            float aimX = c.getAxis(GAMEPAD_AXIS_RIGHT_X);
            float aimY = c.getAxis(GAMEPAD_AXIS_RIGHT_Y);
            if (Math.abs(aimX) > DEADZONE || Math.abs(aimY) > DEADZONE) {
                return gamepadAimAngle;
            }
        }

        Vector3 mouseInWorld = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseInWorld);
        return (float) Math.toDegrees(
                Math.atan2(mouseInWorld.y - getCenterY(), mouseInWorld.x - getCenterX())
        );
    }

    public void setWeapon(Weapons weapon) {
        this.currentWeapon    = weapon;
        this.gun              = Assets.getTexture(weapon.getGunTexturePath());
        this.weaponJustChanged = true;
    }

    public boolean isWeaponJustChanged() {
        if (weaponJustChanged) { weaponJustChanged = false; return true; }
        return false;
    }

    public float getCenterX() { return x + texture.getWidth()  / 2f; }
    public float getCenterY() { return y + texture.getHeight() / 2f; }

    public void resetSpeed()              { speed = DEFAULT_SPEED; }
    public void slowDown(float slowSpeed) { speed = slowSpeed; }

    public boolean isRegenHeart(int heartIndex) {
        return heartIndex >= 0 && heartIndex < MAX_HP && regenHearts[heartIndex];
    }

    public boolean isDead()          { return dead; }
    public Weapons getWeapon()       { return currentWeapon; }
    public int getHp()               { return hp; }
    public int getMaxHp()            { return MAX_HP; }
    public Texture getTexture()      { return texture; }
    public GameTickManager.TickTimer getBayonetCooldown() { return bayonetCooldown; }

    public void dispose() {}
}