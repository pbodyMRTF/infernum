import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

public class TutorialScreen implements Screen {

    GameConfig config;
    private final Jgame game;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private OrthographicCamera camera;

    private Texture playerTex;
    private Texture gunTex;
    private Texture shotgunTex;
    private Texture smgTex;
    private Texture bulletTex;
    private Texture enemyTex;
    private Texture enemy2Tex;
    private Texture enemy3Tex;
    private Texture bloodTex;
    private Texture bayonetTex;
    private Texture heartTex;
    private Texture heartEmptyTex;

    private Sound shootSound;
    private Sound shotgunSound;
    private Sound smgSound;
    private Sound sliceSound;
    private Sound popSound;
    private Sound splatSound;
    private Sound tinSound;
    private float playerX, playerY;
    private float playerScale = 1f;

    private int activeWeapon = 0;
    private float shootCooldown = 0f;

    private boolean showBayonetAnim = false;
    private float bayonetAnimTime = 0f;
    private float bayonetCooldown = 0f;
    private static final float BAYONET_COOLDOWN = 3f;
    private static final float BAYONET_RANGE = 150f;

    private Array<TEnemy> enemies = new Array<>();
    private Array<TBullet> bullets = new Array<>();
    private Array<TBlood> bloods = new Array<>();

    private int phase = 0;
    private float phaseTime = 0f;
    private boolean waitInput = false;

    private boolean killDone = false;
    private float killDelay = 0f;
    private static final float KILL_DELAY = 1.0f;

    private static final int GAMEPAD_BUTTON_A              = 0;
    private static final int GAMEPAD_BUTTON_B              = 1;
    private static final int GAMEPAD_BUTTON_RIGHT_BUMPER   = 5;
    private static final int GAMEPAD_AXIS_LEFT_X           = 0;
    private static final int GAMEPAD_AXIS_LEFT_Y           = 1;
    private static final int GAMEPAD_AXIS_RIGHT_X          = 2;
    private static final int GAMEPAD_AXIS_RIGHT_Y          = 3;
    private static final float DEADZONE                    = 0.2f;

    private boolean prevButtonA          = false;
    private boolean prevButtonB          = false;
    private boolean prevRightBumper      = false;

    private float gamepadAimAngle = 0f;

    public TutorialScreen(final Jgame game) {
        this.game = game;
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = game.font;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        loadConfig();
        loadTextures();
        loadSounds();

        playerX = Gdx.graphics.getWidth()  / 2f - (playerTex.getWidth() * playerScale) / 2f;
        playerY = Gdx.graphics.getHeight() / 2f - (playerTex.getHeight() * playerScale) / 2f;
    }

    private Controller getGamepad() {
        if (Controllers.getControllers().size > 0) {
            return Controllers.getControllers().first();
        }
        return null;
    }

    private boolean gamepadJustPressed(Controller c, int button, boolean prevState) {
        return c != null && c.getButton(button) && !prevState;
    }

    private void loadConfig() {
        Json json = new Json();
        config = json.fromJson(GameConfig.class, Gdx.files.internal("config.json"));
    }

    private void loadTextures() {
        playerTex     = Assets.getTexture(Assets.Textures.PLAYER);
        gunTex        = Assets.getTexture(Assets.Textures.GUN);
        shotgunTex    = Assets.getTexture(Assets.Textures.SHOTGUN);
        smgTex        = Assets.getTexture(Assets.Textures.SMG);
        bulletTex     = Assets.getTexture(Assets.Textures.BULLET);
        enemyTex      = Assets.getTexture(Assets.Textures.ENEMY);
        enemy2Tex     = Assets.getTexture(Assets.Textures.ENEMY2);
        enemy3Tex     = Assets.getTexture(Assets.Textures.ENEMY3);
        bloodTex      = Assets.getTexture(Assets.Textures.BLOOD);
        bayonetTex    = Assets.getTexture(Assets.Textures.BAYONET);
        heartTex      = Assets.getTexture(Assets.Textures.HEART);
        heartEmptyTex = Assets.getTexture(Assets.Textures.HEART_EMPTY);
    }

    private void loadSounds() {
        shootSound   = Assets.getSound(Assets.Sounds.SHOOT);
        shotgunSound = Assets.getSound(Assets.Sounds.SHOTGUNSHOT);
        smgSound     = Assets.getSound(Assets.Sounds.SMGSHOT);
        sliceSound   = Assets.getSound(Assets.Sounds.SLICE);
        popSound     = Assets.getSound(Assets.Sounds.POP);
        splatSound   = Assets.getSound(Assets.Sounds.SPLAT);
        tinSound     = Assets.getSound(Assets.Sounds.TIN);
    }



    private void clearScene() {
        enemies.clear();
        bullets.clear();
        bloods.clear();
        killDone        = false;
        killDelay       = 0f;
        shootCooldown   = 0f;
        bayonetCooldown = 0f;
        showBayonetAnim = false;
    }

    @Override
    public void render(float delta) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        phaseTime += delta;
        shootCooldown -= delta;
        if (shootCooldown < 0f) shootCooldown = 0f;
        bayonetCooldown -= delta;
        if (bayonetCooldown < 0f) bayonetCooldown = 0f;

        updateGamepadAim();
        handleMovement(delta);
        handleShooting();
        handleBayonet();
        updateBullets(delta);
        updateEnemies(delta);
        updateBloods(delta);
        updateBayonetAnim(delta);
        handlePhaseLogic(delta);
        handleInput();

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawHealthBars();
        shapeRenderer.end();

        batch.begin();
        drawBloods();
        drawEnemies();
        drawBullets();
        drawPlayer();
        drawGun();
        drawBayonetAnim();
        drawUI(w, h);
        batch.end();

        updateGamepadButtonStates();
    }

    private void updateGamepadAim() {
        Controller c = getGamepad();
        if (c == null) return;

        float aimX = c.getAxis(GAMEPAD_AXIS_RIGHT_X);
        float aimY = c.getAxis(GAMEPAD_AXIS_RIGHT_Y);
        if (Math.abs(aimX) > DEADZONE || Math.abs(aimY) > DEADZONE) {
            gamepadAimAngle = (float) Math.toDegrees(Math.atan2(-aimY, aimX));
        }
    }

    private void updateGamepadButtonStates() {
        Controller c = getGamepad();
        if (c != null) {
            prevButtonA     = c.getButton(GAMEPAD_BUTTON_A);
            prevButtonB     = c.getButton(GAMEPAD_BUTTON_B);
            prevRightBumper = c.getButton(GAMEPAD_BUTTON_RIGHT_BUMPER);
        } else {
            prevButtonA = prevButtonB = prevRightBumper = false;
        }
    }

    private void handleMovement(float delta) {
        float speed = 350f;
        float mx = 0, my = 0;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) my +=  speed * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -=  speed * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -=  speed * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx +=  speed * delta;

        Controller c = getGamepad();
        if (c != null) {
            float axisX = c.getAxis(GAMEPAD_AXIS_LEFT_X);
            float axisY = c.getAxis(GAMEPAD_AXIS_LEFT_Y);
            if (Math.abs(axisX) > DEADZONE) mx += axisX * speed * delta;
            if (Math.abs(axisY) > DEADZONE) my -= axisY * speed * delta;
        }

        playerX += mx;
        playerY += my;

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float scaledWidth  = playerTex.getWidth()  * playerScale;
        float scaledHeight = playerTex.getHeight() * playerScale;
        playerX = MathUtils.clamp(playerX, 0, w - scaledWidth);
        playerY = MathUtils.clamp(playerY, 0, h - scaledHeight);
    }

    private void handleShooting() {
        if (phase != 3 && phase != 6 && phase != 9) return;
        if (killDone) return;
        if (shootCooldown > 0f) return;

        boolean isAuto;
        int count;
        float spread;
        int damage;
        float rate;
        Sound sound;

        switch (activeWeapon) {
            case 1:
                isAuto = false;
                count  = 4;
                spread = 15f;
                damage = 15;
                rate   = 0.45f;
                sound  = shotgunSound;
                break;
            case 2:
                isAuto = true;
                count  = 1;
                spread = 0.07f;
                damage = 15;
                rate   = 0.08f;
                sound  = smgSound;
                break;
            default:
                isAuto = false;
                count  = 1;
                spread = 0f;
                damage = 8;
                rate   = 0.20f;
                sound  = shootSound;
                break;
        }

        Controller c = getGamepad();
        boolean fired;
        if (isAuto) {
            fired = Gdx.input.isButtonPressed(Input.Buttons.LEFT)
                    || (c != null && c.getButton(5));
        } else {
            fired = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                    || gamepadJustPressed(c, 5, prevRightBumper);
        }

        if (!fired) return;

        sound.play(0.7f);

        float px = getPlayerCenterX();
        float py = getPlayerCenterY();
        float baseAngle = getAimAngle();

        for (int i = 0; i < count; i++) {
            float spreadAngle = (count > 1) ? MathUtils.random(-spread, spread) : 0f;
            float finalAngle  = baseAngle + spreadAngle;
            float rad         = (float) Math.toRadians(finalAngle);
            bullets.add(new TBullet(px, py, (float) Math.cos(rad), (float) Math.sin(rad), damage));
        }

        shootCooldown = rate;
    }

    private void handleBayonet() {
        if (phase != 12) return;
        if (killDone) return;
        if (bayonetCooldown > 0f) return;

        Controller c = getGamepad();
        boolean bayonetTriggered = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_RIGHT_BUMPER, prevRightBumper);

        if (!bayonetTriggered) return;

        sliceSound.play(1.2f);

        showBayonetAnim = true;
        bayonetAnimTime = 0f;
        bayonetCooldown = BAYONET_COOLDOWN;

        float px = getPlayerCenterX();
        float py = getPlayerCenterY();

        for (TEnemy e : enemies) {
            if (e.hp <= 0) continue;
            float dist = Vector2.dst(e.x + 32, e.y + 32, px, py);
            if (dist < BAYONET_RANGE) {
                e.hp = 0;
                spawnBlood(e.x + 32, e.y + 32, 8);
                popSound.play(1f);
            }
        }

        if (allDead()) {
            killDone  = true;
            killDelay = KILL_DELAY;
        }
    }

    private float getAimAngle() {
        Controller c = getGamepad();
        if (c != null) {
            float aimX = c.getAxis(GAMEPAD_AXIS_RIGHT_X);
            float aimY = c.getAxis(GAMEPAD_AXIS_RIGHT_Y);
            if (Math.abs(aimX) > DEADZONE || Math.abs(aimY) > DEADZONE) {
                return gamepadAimAngle;
            }
        }

        float px = getPlayerCenterX();
        float py = getPlayerCenterY();
        Vector3 mouseWorld = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        return (float) Math.toDegrees(Math.atan2(mouseWorld.y - py, mouseWorld.x - px));
    }

    private void updateBayonetAnim(float delta) {
        if (!showBayonetAnim) return;
        bayonetAnimTime += delta;
        if (bayonetAnimTime >= 0.3f) showBayonetAnim = false;
    }

    private void updateBullets(float delta) {
        float spd = 650f;
        for (int i = bullets.size - 1; i >= 0; i--) {
            TBullet b = bullets.get(i);
            b.x += b.dx * spd * delta;
            b.y += b.dy * spd * delta;

            if (b.x < -30 || b.x > Gdx.graphics.getWidth()  + 30 ||
                    b.y < -30 || b.y > Gdx.graphics.getHeight() + 30) {
                bullets.removeIndex(i);
                continue;
            }

            for (TEnemy e : enemies) {
                if (e.hp <= 0) continue;
                if (Vector2.dst(b.x, b.y, e.x + 32, e.y + 32) < 36f) {
                    e.hp -= b.damage;
                    bullets.removeIndex(i);
                    if (e.hp <= 0) {
                        spawnBlood(e.x + 32, e.y + 32, 8);
                        popSound.play(0.7f);
                        if (allDead()) {
                            killDone  = true;
                            killDelay = KILL_DELAY;
                        }
                    }
                    break;
                }
            }
        }
    }

    private void updateEnemies(float delta) {
        float px = getPlayerCenterX();
        float py = getPlayerCenterY();

        for (TEnemy e : enemies) {
            if (e.hp <= 0) continue;
            float dx  = px - (e.x + 32);
            float dy  = py - (e.y + 32);
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len == 0) continue;
            dx /= len;
            dy /= len;

            float spd;
            switch (e.type) {
                case 3:  spd = 110f; break;
                case 2:  spd =  65f; break;
                default: spd =  75f; break;
            }
            e.x += dx * spd * delta;
            e.y += dy * spd * delta;
        }
    }

    private void updateBloods(float delta) {
        for (int i = bloods.size - 1; i >= 0; i--) {
            TBlood b = bloods.get(i);
            b.life -= delta * 1.4f;
            b.x    += b.vx * delta;
            b.y    += b.vy * delta;
            b.vy   -= 90f * delta;
            if (b.life <= 0f) bloods.removeIndex(i);
        }
    }

    private boolean allDead() {
        if (enemies.size == 0) return false;
        for (TEnemy e : enemies) if (e.hp > 0) return false;
        return true;
    }

    private void handlePhaseLogic(float delta) {
        if (killDone) {
            killDelay -= delta;
            if (killDelay <= 0f) advancePhase();
            return;
        }

        switch (phase) {
            case 0:
            case 1:
                waitInput = true;
                break;

            case 2:
                waitInput = true;
                if (enemies.size == 0) {
                    activeWeapon = 2;
                    spawnEnemy(1, Gdx.graphics.getWidth() / 2f + 150, Gdx.graphics.getHeight() / 2f + 160);
                }
                break;
            case 3:
                waitInput = false;
                if (enemies.size == 0)
                    spawnEnemy(1, Gdx.graphics.getWidth() / 2f + 150, Gdx.graphics.getHeight() / 2f + 160);
                break;
            case 4:
                waitInput = true;
                clearScene();
                break;

            case 5:
                waitInput = true;
                if (enemies.size == 0) {
                    activeWeapon = 1;
                    spawnEnemy(2, Gdx.graphics.getWidth() / 2f + 150, Gdx.graphics.getHeight() / 2f + 160);
                }
                break;
            case 6:
                waitInput = false;
                if (enemies.size == 0)
                    spawnEnemy(2, Gdx.graphics.getWidth() / 2f + 150, Gdx.graphics.getHeight() / 2f + 160);
                break;
            case 7:
                waitInput = true;
                clearScene();
                break;

            case 8:
                waitInput = true;
                if (enemies.size == 0) {
                    activeWeapon = 0;
                    spawnEnemy(3, Gdx.graphics.getWidth() / 2f + 150, Gdx.graphics.getHeight() / 2f + 160);
                }
                break;
            case 9:
                waitInput = false;
                if (enemies.size == 0)
                    spawnEnemy(3, Gdx.graphics.getWidth() / 2f + 150, Gdx.graphics.getHeight() / 2f + 160);
                break;
            case 10:
                waitInput = true;
                clearScene();
                break;

            case 11:
                waitInput = true;
                clearScene();
                break;

            case 12:
                waitInput = false;
                if (enemies.size == 0) {
                    float cx = Gdx.graphics.getWidth()  / 2f;
                    float cy = Gdx.graphics.getHeight() / 2f;
                    spawnEnemy(1, cx - 140, cy + 180);
                    spawnEnemy(2, cx,        cy + 200);
                    spawnEnemy(3, cx + 140,  cy + 180);
                }
                break;

            case 13:
                waitInput = true;
                clearScene();
                break;
        }
    }

    private void advancePhase() {
        phase++;
        phaseTime = 0f;
        waitInput = false;
        killDone  = false;
        killDelay = 0f;

        if (phase > 13) {
            game.setScreen(new MainMenuScreen(game)); // Eğitim bitti → ana menü
        }
    }

    private void handleInput() {
        Controller c = getGamepad();

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) Gdx.app.exit();

        boolean back = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_B, prevButtonB);

        if (back) {
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        if (!waitInput) return;

        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_A, prevButtonA);

        if (!confirm) return;

        clearScene();
        advancePhase();
    }

    private void drawBloods() {
        for (TBlood b : bloods) {
            batch.setColor(1f, 0.1f, 0.1f, MathUtils.clamp(b.life, 0f, 1f));
            batch.draw(bloodTex, b.x, b.y);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawEnemies() {
        for (TEnemy e : enemies) {
            if (e.hp <= 0) continue;
            Texture t;
            switch (e.type) {
                case 2:  t = enemy2Tex; break;
                case 3:  t = enemy3Tex; break;
                default: t = enemyTex;  break;
            }
            batch.draw(t, e.x, e.y);
        }
    }

    private void drawBullets() {
        for (TBullet b : bullets) {
            batch.draw(bulletTex, b.x, b.y);
        }
    }

    private void drawPlayer() {
        batch.draw(playerTex, playerX, playerY,
                playerTex.getWidth()  * playerScale,
                playerTex.getHeight() * playerScale);
    }

    private void drawGun() {
        Texture g;
        float scale;
        switch (activeWeapon) {
            case 1:  g = shotgunTex; scale = 2f;   break;
            case 2:  g = smgTex;     scale = 1.5f; break;
            default: g = gunTex;     scale = 1f;   break;
        }

        float px    = getPlayerCenterX();
        float py    = getPlayerCenterY();
        float angle = getAimAngle();

        batch.draw(g,
                px, py,
                0, g.getHeight() / 2f,
                g.getWidth(), g.getHeight(),
                scale, scale,
                angle,
                0, 0,
                g.getWidth(), g.getHeight(),
                false, false);
    }

    private void drawBayonetAnim() {
        if (!showBayonetAnim) return;

        float px = getPlayerCenterX();
        float py = getPlayerCenterY();

        float bicakBoyutuX    = 16f;
        float bicakBoyutuY    = 32f;
        float yorungeUzakligi = 60f;

        float rotation = (bayonetAnimTime / 0.3f) * 360f * 1.5f;
        float alpha    = 1f - (bayonetAnimTime / 0.3f);

        batch.setColor(1f, 1f, 1f, alpha);

        float drawX = px + MathUtils.cosDeg(rotation) * yorungeUzakligi;
        float drawY = py + MathUtils.sinDeg(rotation) * yorungeUzakligi;

        float sabitAci = -90f;

        batch.draw(bayonetTex,
                drawX - (bicakBoyutuX / 2),
                drawY - (bicakBoyutuY / 2),
                bicakBoyutuX / 2, bicakBoyutuY / 2,
                bicakBoyutuX, bicakBoyutuY,
                1f, 1f,
                rotation + sabitAci,
                0, 0,
                bayonetTex.getWidth(), bayonetTex.getHeight(),
                false, false);

        batch.setColor(Color.WHITE);
    }

    private void drawHealthBars() {
        for (TEnemy e : enemies) {
            if (e.hp <= 0) continue;

            float bx = e.x;
            float by = e.y + 68f;
            float bw = 64f;
            float bh = 7f;

            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.85f);
            shapeRenderer.rect(bx, by, bw, bh);

            float ratio = MathUtils.clamp((float) e.hp / e.maxHp, 0f, 1f);
            if (ratio > 0.5f)
                shapeRenderer.setColor(0.2f, 0.85f, 0.2f, 1f);
            else
                shapeRenderer.setColor(0.85f, 0.3f, 0.1f, 1f);
            shapeRenderer.rect(bx, by, bw * ratio, bh);
        }
    }

    private void drawUI(float w, float h) {
        switch (phase) {
            case 0:
                drawLine("Tutorial", h - 60,  1.0f, new Color(1f, 0.4f, 0.2f, 1f));
                drawLine("Düşmanları öldür. Her tip farklı silahla daha etkili öldürülür.", h - 90, 0.7f, Color.WHITE);
                drawLine("WASD = Hareket   Sol tık / RT = Ateş   Sağ tık / RB = Bayonet", h - 115, 0.65f, Color.WHITE);
                drawHint(h);
                break;
            case 1:
                drawLine("WASD / Sol Analog ile hareket et, mouse / Sağ Analog ile nişana al.", h - 60, 0.75f, Color.WHITE);
                drawHint(h);
                break;
            case 2:
                drawLine("Düşman Tip 1[Günahkâr] — SMG ile en etkili öldürülür  [NUM 3]", h - 60, 0.72f, new Color(0.3f, 0.7f, 1f, 1f));
                drawHint(h);
                break;
            case 3:
                drawLine("SMG ile ateş et, düşmanı öldür!", h - 60, 0.7f, new Color(0.3f, 0.7f, 1f, 1f));
                drawWeaponTag(w, h);
                break;
            case 4:
                drawLine("SMG 15 hasar / mermi", h - 60, 0.72f, new Color(0.5f, 1f, 0.5f, 1f));
                drawDamageTable(1, w, h);
                drawHint(h);
                break;
            case 5:
                drawLine("Düşman Tip 2[Zırhlı Robot] — Pompalı ile en etkili öldürülür  [NUM 2]", h - 60, 0.72f, new Color(1f, 0.6f, 0.2f, 1f));
                drawHint(h);
                break;
            case 6:
                drawLine("Pompalı ile ateş et, düşmanı öldür!", h - 60, 0.7f, new Color(1f, 0.6f, 0.2f, 1f));
                drawWeaponTag(w, h);
                break;
            case 7:
                drawLine("Pompalı  →  Tip 2  →  15 hasar / mermi", h - 60, 0.72f, new Color(0.5f, 1f, 0.5f, 1f));
                drawDamageTable(2, w, h);
                drawHint(h);
                break;
            case 8:
                drawLine("Düşman Tip 3[Uyuşturucu Bağımlısı Günahkâr] — Tabanca ile en etkili öldürülür  [NUM 1]", h - 60, 0.72f, new Color(0.8f, 0.3f, 1f, 1f));
                drawHint(h);
                break;
            case 9:
                drawLine("Tabanca ile ateş et, düşmanı öldür!", h - 60, 0.7f, new Color(0.9f, 0.9f, 0.3f, 1f));
                drawWeaponTag(w, h);
                break;
            case 10:
                drawLine("Tabanca  →  Tip 3  →  8 hasar / mermi", h - 60, 0.72f, new Color(0.5f, 1f, 0.5f, 1f));
                drawDamageTable(3, w, h);
                drawHint(h);
                break;
            case 11:
                drawLine("Bayonet — Sağ tık / RB ile yakın mesafe saldırı", h - 60, 0.72f, new Color(0.9f, 0.5f, 0.5f, 1f));
                drawLine("2+ öldürürsen can yeniler, 3+ öldürürsen 2 can yeniler.", h - 85, 0.65f, Color.WHITE);
                drawHint(h);
                break;
            case 12:
                drawLine("Düşmanlara yakın git, sağ tık / RB ile bayonet kullan!", h - 60, 0.7f, new Color(0.9f, 0.5f, 0.5f, 1f));
                drawBayonetBar(w, h);
                break;
            case 13:
                drawLine("Hazır?", h - 55, 1.0f, new Color(0.4f, 1f, 0.4f, 1f));
                drawSummary(w, h);
                drawHint(h);
                break;
        }


    }

    private void drawLine(String text, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(batch, text, 24, y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawHint(float h) {
        float blink = MathUtils.sin(phaseTime * 4f) * 0.25f + 0.75f;
        font.getData().setScale(0.58f);
        font.setColor(1f, 1f, 1f, blink);
        font.draw(batch, "SPACE / ENTER / A ile devam", 24, 28);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawWeaponTag(float w, float h) {
        String name;
        Color c;
        switch (activeWeapon) {
            case 1:  name = "POMPA";   c = new Color(1f, 0.6f, 0.2f, 1f); break;
            case 2:  name = "SMG";     c = new Color(0.3f, 0.7f, 1f, 1f); break;
            default: name = "TABANCA"; c = new Color(0.9f, 0.9f, 0.3f, 1f); break;
        }
        font.getData().setScale(0.7f);
        font.setColor(c);
        font.draw(batch, name, 24, 60);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawBayonetBar(float w, float h) {
        float prog = 1f - (bayonetCooldown / BAYONET_COOLDOWN);
        if (prog < 0f) prog = 0f;
        if (prog > 1f) prog = 1f;
        String label = prog >= 1f ? "Bayonet: HAZIR" : String.format("Bayonet: %.1fs", bayonetCooldown);
        font.getData().setScale(0.6f);
        font.setColor(prog >= 1f ? new Color(0.4f, 0.9f, 0.4f, 1f) : new Color(0.9f, 0.5f, 0.2f, 1f));
        font.draw(batch, label, 24, 60);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawDamageTable(int enemyType, float w, float h) {
        String[] silahlar = {"SMG [3]", "Pompalı [2]", "Tabanca [1]"};
        String[] hasarlar;
        int bestIdx;
        switch (enemyType) {
            case 1:  hasarlar = new String[]{"15", "1", "3"};     bestIdx = 0; break;
            case 2:  hasarlar = new String[]{"1", "15", "5"};     bestIdx = 1; break;
            default: hasarlar = new String[]{"0.5", "0.3", "8"};  bestIdx = 2; break;
        }
        Color[] sc = {
                new Color(0.3f, 0.7f, 1f, 1f),
                new Color(1f, 0.6f, 0.2f, 1f),
                new Color(0.9f, 0.9f, 0.3f, 1f)
        };

        float startY = h - 115;
        font.getData().setScale(0.65f);
        for (int i = 0; i < 3; i++) {
            boolean best = (i == bestIdx);
            font.setColor(sc[i]);
            font.draw(batch, silahlar[i], 24, startY - i * 22);
            font.setColor(best ? new Color(0.4f, 1f, 0.4f, 1f) : new Color(0.6f, 0.6f, 0.6f, 1f));
            font.draw(batch, hasarlar[i] + " hasar" + (best ? " *" : ""), 160, startY - i * 22);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawSummary(float w, float h) {
        String[][] rows = {
                {"Tip 1", "→  SMG  [3]",    "15 hasar"},
                {"Tip 2", "→  Pompalı [2]", "15 hasar"},
                {"Tip 3", "→  Tabanca [1]", "8 hasar"},
                {"Bayonet", "→  Sağ tık / RB", "Yakın alan"}
        };
        Color[] sc = {
                new Color(0.3f, 0.7f, 1f, 1f),
                new Color(1f, 0.6f, 0.2f, 1f),
                new Color(0.9f, 0.9f, 0.3f, 1f),
                new Color(0.9f, 0.5f, 0.5f, 1f)
        };

        float startY = h - 100;
        font.getData().setScale(0.7f);
        for (int i = 0; i < rows.length; i++) {
            float y = startY - i * 24;
            font.setColor(Color.WHITE);
            font.draw(batch, rows[i][0], 24, y);
            font.setColor(sc[i]);
            font.draw(batch, rows[i][1], 100, y);
            font.setColor(0.5f, 0.9f, 0.5f, 1f);
            font.draw(batch, rows[i][2], 280, y);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private float getPlayerCenterX() {
        return playerX + (playerTex.getWidth() * playerScale) / 2f;
    }

    private float getPlayerCenterY() {
        return playerY + (playerTex.getHeight() * playerScale) / 2f;
    }

    private void spawnEnemy(int type, float x, float y) {
        int maxHp;
        switch (type) {
            case 1:  maxHp = 15; break;
            case 2:  maxHp = 15; break;
            case 3:  maxHp = 8;  break;
            default: maxHp = 10; break;
        }
        enemies.add(new TEnemy(type, x, y, maxHp));
    }

    private void spawnBlood(float x, float y, int count) {
        for (int i = 0; i < count; i++)
            bloods.add(new TBlood(x + MathUtils.random(-16f, 16f), y + MathUtils.random(-16f, 16f)));
    }

    static class TEnemy {
        int type;
        float x, y;
        int hp, maxHp;
        TEnemy(int type, float x, float y, int maxHp) {
            this.type  = type;
            this.x     = x;
            this.y     = y;
            this.hp    = maxHp;
            this.maxHp = maxHp;
        }
    }

    static class TBullet {
        float x, y, dx, dy;
        int damage;
        TBullet(float x, float y, float dx, float dy, int damage) {
            this.x      = x;
            this.y      = y;
            this.dx     = dx;
            this.dy     = dy;
            this.damage = damage;
        }
    }

    static class TBlood {
        float x, y, vx, vy, life = 1f;
        TBlood(float x, float y) {
            this.x  = x;
            this.y  = y;
            this.vx = MathUtils.random(-50f, 50f);
            this.vy = MathUtils.random(10f, 90f);
        }
    }

    @Override public void show()   {}
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}
    @Override public void resize(int w, int h) {
        camera.setToOrtho(false, w, h);
    }
    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }
}