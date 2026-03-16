import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/**
 * TutorialScreen — GameScreen mimarisine yakın remaster.
 *
 * Değişiklikler / iyileştirmeler:
 *  - Player nesnesi artık gerçek Player sınıfı (GameScreen ile aynı).
 *  - GameTickManager kullanılıyor (delta-time yerine tick tabanlı zamanlama).
 *  - shootCooldown / hitCooldown / slowdownTimer / deathTimer → TickTimer.
 *  - Weapons sistemi GameScreen ile tamamen aynı.
 *  - Damage tablosu artık Weapons.getDamageAgainst() üzerinden çekiliyor.
 *  - ShaderProgram (red.vsh/red.fsh) düşmanlara uygulanıyor.
 *  - ExtendViewport / iki kamera (world + UI) sistemi.
 *  - Hotbar, can göstergesi, bayonet cooldown bar GameScreen'den birebir.
 *  - Tüm düşmanlar Enemy / Enemy2 / Enemy3 sınıfları üzerinden.
 *  - BloodParticle ve toz efektleri.
 *  - Player hasar alabilir, can bitince phase ilerlemez, ekran kararır.
 *  - Phase geçişleri "SPACE / ENTER / A" ile (waitInput=true) ya da tüm
 *    düşmanlar ölünce otomatik (killDone mantığı).
 */
public class TutorialScreen implements Screen {

    // ── Sabitler ──────────────────────────────────────────────────────────────
    private static final float WORLD_WIDTH    = 1024f;
    private static final float WORLD_HEIGHT   = 768f;
    private static final float UI_WIDTH       = 1024f;
    private static final float UI_HEIGHT      = 768f;
    private static final float BAYONET_RANGE  = 150f;
    private static final float KILL_DELAY_SEC = 1.0f;

    private static final int GAMEPAD_BUTTON_A            = 0;
    private static final int GAMEPAD_BUTTON_B            = 1;
    private static final int GAMEPAD_AXIS_LEFT_X         = 0;
    private static final int GAMEPAD_AXIS_LEFT_Y         = 1;

    // ── Temel bileşenler ──────────────────────────────────────────────────────
    private final Jgame game;
    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;
    private GameTickManager tickManager;
    private ShaderProgram enemyShader;
    private float shaderTime = 0f;

    // ── Kameralar & viewport ──────────────────────────────────────────────────
    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;
    private ExtendViewport viewport;
    private ExtendViewport uiViewport;

    // ── Player ────────────────────────────────────────────────────────────────
    private Player player;

    // ── Timerlar ──────────────────────────────────────────────────────────────
    private GameTickManager.TickTimer shootCooldown;
    private GameTickManager.TickTimer hitCooldown;
    private GameTickManager.TickTimer slowdownTimer;
    private GameTickManager.TickTimer deathTimer;
    private GameTickManager.TickTimer bayonetCooldown;
    private GameTickManager.TickTimer killDelayTimer;   // öldürme sonrası bekleme

    private static final int SHOOT_COOLDOWN_TICKS   = 16;
    private static final int HIT_COOLDOWN_TICKS     = 16;
    private static final int SLOWDOWN_TICKS         = 40;
    private static final int DEATH_DELAY_TICKS      = 60;
    private static final int BAYONET_COOLDOWN_TICKS = 60;
    private static final int KILL_DELAY_TICKS       = 20; // ~1 saniye @ 20 tps

    // ── Objeler ───────────────────────────────────────────────────────────────
    private Array<Enemy>         enemies  = new Array<>();
    private Array<Enemy2>        enemies2 = new Array<>();
    private Array<Enemy3>        enemies3 = new Array<>();
    private Array<Bullet>        bullets  = new Array<>();
    private Array<BloodParticle> bloods   = new Array<>();
    private Array<toz>           tozlar   = new Array<>();

    // ── Textureler ────────────────────────────────────────────────────────────
    private Texture bulletTex;
    private Texture enemyTex, enemy2Tex, enemy3Tex;
    private Texture bloodTex, tozTex;
    private Texture heartTex, heartEmptyTex, regenHeartTex;
    private Texture bayonetTex;
    private Texture Hotbar1, Hotbar2, Hotbar3;

    // ── Sesler ────────────────────────────────────────────────────────────────
    private Sound shootSound, ShotgunSound, SmgSound;
    private Sound popSound, woodSound, sliceSound, tinSound, splatSound;

    // ── Bayonet animasyon ─────────────────────────────────────────────────────
    private boolean showBayonetAnim = false;
    private float   bayonetAnimTime = 0f;

    // ── Tutorial faz yönetimi ─────────────────────────────────────────────────
    private int     phase         = 0;
    private float   phaseTime     = 0f;    // blink efekti için
    private boolean waitInput     = true;
    private boolean killDone      = false;
    private boolean deathTimerStarted = false;
    private boolean isSlowed      = false;

    // Gamepad önceki state
    private boolean prevButtonA = false;
    private boolean prevButtonB = false;
    // map
    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private TiledMapTileLayer groundLayer;
    private TiledMapTileLayer wallLayer;
    private TiledMapTileLayer lowObstacleLayer;
    private ShaderProgram mapShader;

    // ─────────────────────────────────────────────────────────────────────────

    public TutorialScreen(final Jgame game) {
        this.game = game;

        batch         = new SpriteBatch();
        font          = game.font;
        shapeRenderer = new ShapeRenderer();

        // Kameralar
        camera   = new OrthographicCamera();
        viewport = new ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);

        uiCamera   = new OrthographicCamera();
        uiViewport = new ExtendViewport(UI_WIDTH, UI_HEIGHT, uiCamera);
        uiCamera.position.set(UI_WIDTH / 2f, UI_HEIGHT / 2f, 0);
        uiCamera.update();

        // Tick sistemi
        tickManager = new GameTickManager();
        tickManager.addListener(new GameTickManager.TickListener() {
            @Override
            public void onTick(int currentTick) {
                handleTick(currentTick);
            }
        });

        // Timerlar
        shootCooldown  = new GameTickManager.TickTimer(SHOOT_COOLDOWN_TICKS);
        hitCooldown    = new GameTickManager.TickTimer(HIT_COOLDOWN_TICKS);
        slowdownTimer  = new GameTickManager.TickTimer(SLOWDOWN_TICKS);
        deathTimer     = new GameTickManager.TickTimer(DEATH_DELAY_TICKS);
        bayonetCooldown = new GameTickManager.TickTimer(BAYONET_COOLDOWN_TICKS);
        killDelayTimer = new GameTickManager.TickTimer(KILL_DELAY_TICKS);

        loadAssets();
        spawnPlayer();
    }

    // ── Asset yükleme ─────────────────────────────────────────────────────────

    private void loadAssets() {
        shootSound   = Assets.getSound(Assets.Sounds.SHOOT);
        ShotgunSound = Assets.getSound(Assets.Sounds.SHOTGUNSHOT);
        SmgSound     = Assets.getSound(Assets.Sounds.SMGSHOT);
        popSound     = Assets.getSound(Assets.Sounds.POP);
        woodSound    = Assets.getSound(Assets.Sounds.WOOD);
        sliceSound   = Assets.getSound(Assets.Sounds.SLICE);
        tinSound     = Assets.getSound(Assets.Sounds.TIN);
        splatSound   = Assets.getSound(Assets.Sounds.SPLAT);

        bulletTex     = Assets.getTexture(Assets.Textures.BULLET);
        enemyTex      = Assets.getTexture(Assets.Textures.ENEMY);
        enemy2Tex     = Assets.getTexture(Assets.Textures.ENEMY2);
        enemy3Tex     = Assets.getTexture(Assets.Textures.ENEMY3);
        bloodTex      = Assets.getTexture(Assets.Textures.BLOOD);
        tozTex        = Assets.getTexture(Assets.Textures.TOZ);
        heartTex      = Assets.getTexture(Assets.Textures.HEART);
        heartEmptyTex = Assets.getTexture(Assets.Textures.HEART_EMPTY);
        regenHeartTex = Assets.getTexture(Assets.Textures.REGEN_KALP);
        bayonetTex    = Assets.getTexture(Assets.Textures.BAYONET);

        Hotbar1 = Assets.getTexture(Assets.Textures.HOTBAR1);
        Hotbar2 = Assets.getTexture(Assets.Textures.HOTBAR2);
        Hotbar3 = Assets.getTexture(Assets.Textures.HOTBAR3);
    }

    // ── Player oluşturma ──────────────────────────────────────────────────────

    private void spawnPlayer() {
        // Tutorial için ekranın ortası — harita yoksa sabit koordinat
        float cx = WORLD_WIDTH  / 2f - 256f;
        float cy = WORLD_HEIGHT / 2f - 32f;

        Texture playerTex = Assets.getTexture(Assets.Textures.PLAYER);
        player = new Player(cx, cy, playerTex, 0);
        player.setHitCooldown(hitCooldown);
        player.setBayonetCooldown(bayonetCooldown);
        player.setWeapon(new Weapons(Weapons.WeaponType.PISTOL));

        player.setBayonetCallback(new Player.BayonetCallback() {
            @Override
            public int onBayonetUse() {
                return useBayonet();
            }
        });
    }

    // ── Tick işleyici ─────────────────────────────────────────────────────────

    private void handleTick(int currentTick) {
        checkTimers(currentTick);
    }

    private void checkTimers(int currentTick) {
        // Ölüm → menüye dön
        if (deathTimer.isRunning() && deathTimer.isFinished(currentTick)) {
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        // Yavaşlama bitti
        if (slowdownTimer.isRunning() && slowdownTimer.isFinished(currentTick)) {
            player.resetSpeed();
            isSlowed = false;
            slowdownTimer.stop();
        }

        // Silah değişince shoot cooldown sıfırla
        if (player.isWeaponJustChanged()) shootCooldown.stop();

        if (shootCooldown.isRunning()   && shootCooldown.isFinished(currentTick))   shootCooldown.stop();
        if (hitCooldown.isRunning()     && hitCooldown.isFinished(currentTick))     hitCooldown.stop();
        if (bayonetCooldown.isRunning() && bayonetCooldown.isFinished(currentTick)) bayonetCooldown.stop();

        // Kill-delay bitti → sonraki faz
        if (killDelayTimer.isRunning() && killDelayTimer.isFinished(currentTick)) {
            killDelayTimer.stop();
            killDone = false;
            advancePhase();
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        tickManager.update(delta);
        shaderTime += delta;
        phaseTime  += delta;

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) Gdx.app.exit();

        Controller c = getGamepad();
        boolean back = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_B, prevButtonB);
        if (back) {
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        // Güncelleme
        updatePlayer(delta);
        handleShooting();
        updateBullets(delta);
        updateEnemies(delta);
        handleCollisions();
        updateBayonetAnim(delta);
        updateBloodParticles(delta);
        updateToz(delta);
        cleanupDeadObjects();
        handlePhaseLogic();
        handleTutorialInput();

        // Kamera: player'ı ortala, dünya sınırlarına kilitle
        camera.position.set(player.getCenterX(), player.getCenterY(), 0);
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        camera.position.x = MathUtils.clamp(camera.position.x, viewport.getWorldWidth()  / 2f, mapWidth  - viewport.getWorldWidth()  / 2f);
        camera.position.y = MathUtils.clamp(camera.position.y, viewport.getWorldHeight() / 2f, mapHeight - viewport.getWorldHeight() / 2f);
        camera.update();

        renderGame();

        // Gamepad state güncelle
        if (c != null) {
            prevButtonA = c.getButton(GAMEPAD_BUTTON_A);
            prevButtonB = c.getButton(GAMEPAD_BUTTON_B);
        } else {
            prevButtonA = prevButtonB = false;
        }
    }
    private void updatePlayer(float delta) {
        player.update(delta, wallLayer, lowObstacleLayer);

        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        player.x = MathUtils.clamp(player.x, 0, mapWidth  - player.getTexture().getWidth());
        player.y = MathUtils.clamp(player.y, 0, mapHeight - player.getTexture().getHeight());
    }

    private void handleShooting() {
        if (player.dead) return;
        Weapons w = player.getWeapon();
        if (w == null) return;

        // Sadece ateş fazlarında
        if (!isShootingPhase()) return;

        boolean triggerNow = player.isTriggerPressed();
        boolean shootInput;
        if (w.isAutomatic()) {
            shootInput = Gdx.input.isButtonPressed(Input.Buttons.LEFT) || triggerNow;
        } else {
            shootInput = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                    || (triggerNow && !player.prevTriggerFired);
        }
        player.prevTriggerFired = triggerNow;

        if (shootInput && !shootCooldown.isRunning()) {
            float baseAngle = player.getAngleToMouse(camera);

            Bullet.BulletType bulletType;
            switch (w.getType()) {
                case SHOTGUN:
                    ShotgunSound.play(0.7f);
                    bulletType = Bullet.BulletType.AMMO;
                    break;
                case SMG:
                    SmgSound.play(0.7f);
                    bulletType = Bullet.BulletType.AMMO_SMG;
                    break;
                default:
                    shootSound.play(0.7f);
                    bulletType = Bullet.BulletType.AMMO_PISTOL;
                    break;
            }

            for (int i = 0; i < w.getBulletCount(); i++) {
                float spread = MathUtils.random(-w.getBulletSpread(), w.getBulletSpread());
                bullets.add(new Bullet(
                        player.getCenterX(), player.getCenterY(),
                        baseAngle + spread, bulletType
                ));
            }

            shootCooldown = new GameTickManager.TickTimer(w.getFireRateTicks());
            shootCooldown.start(tickManager.getCurrentTick());
        }
    }

    private boolean isShootingPhase() {
        // Tutorial fazlarında yalnızca "aktif savaş" fazlarında ateş edilebilir
        return phase == 3 || phase == 6 || phase == 9 || phase == 12;
    }

    private void updateBullets(float delta) {
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;
        for (Bullet b : bullets) b.update(delta, wallLayer, mapWidth, mapHeight);
    }

    private void updateEnemies(float delta) {
        for (Enemy  e : enemies)  e.update(delta, player.x, player.y);
        for (Enemy2 e : enemies2) e.update(delta, player.x, player.y);
        for (Enemy3 e : enemies3) e.update(delta, player.x, player.y);
    }

    private void updateBloodParticles(float delta) {
        for (BloodParticle b : bloods) b.update(delta);
    }

    private void updateToz(float delta) {
        for (toz t : tozlar) t.update(delta);
    }

    private void updateBayonetAnim(float delta) {
        if (showBayonetAnim) {
            bayonetAnimTime += delta;
            if (bayonetAnimTime >= 0.3f) showBayonetAnim = false;
        }
    }

    // ── Çarpışma ──────────────────────────────────────────────────────────────

    private void handleCollisions() {
        handleBulletEnemyCollision();
        handlePlayerEnemyCollision();
        handlePlayerBloodCollision();
        checkAllDeadForPhase();
    }

    private void handleBulletEnemyCollision() {
        for (Enemy e : enemies) {
            for (Bullet b : bullets) {
                if (!e.dead && checkBulletHit(e.x, e.y, b.x, b.y)) {
                    int dmg = bulletDamageForEnemy1(b.getBulletType());
                    e.hp -= dmg;
                    if (e.hp <= 0) { createBloodEffect(e.x, e.y); popSound.play(0.7f); e.dead = true; }
                    b.dead = true;
                }
            }
        }
        for (Enemy2 e : enemies2) {
            for (Bullet b : bullets) {
                if (!e.dead && checkBulletHit(e.x, e.y, b.x, b.y)) {
                    int dmg = bulletDamageForEnemy2(b.getBulletType());
                    e.hp -= dmg;
                    if (e.hp <= 0) { createTozEffect(e.x, e.y); createBloodEffect(e.x, e.y); popSound.play(0.7f); e.dead = true; }
                    b.dead = true;
                }
            }
        }
        for (Enemy3 e : enemies3) {
            for (Bullet b : bullets) {
                if (!e.dead && checkBulletHit(e.x, e.y, b.x, b.y)) {
                    float dmg = bulletDamageForEnemy3(b.getBulletType());
                    e.hp -= dmg;
                    if (e.hp <= 0) { createBloodEffect(e.x, e.y); popSound.play(0.7f); e.dead = true; }
                    b.dead = true;
                }
            }
        }
    }

    private int   bulletDamageForEnemy1(Bullet.BulletType t) { switch(t) { case AMMO_SMG: splatSound.play(); return 15; case AMMO_PISTOL: tinSound.play(1f); return 3; default: tinSound.play(1f); return 1; } }
    private int   bulletDamageForEnemy2(Bullet.BulletType t) { switch(t) { case AMMO: splatSound.play(); return 15; case AMMO_SMG: tinSound.play(1f); popSound.play(0.2f); return 1; default: tinSound.play(1f); popSound.play(0.2f); return 5; } }
    private float bulletDamageForEnemy3(Bullet.BulletType t) { switch(t) { case AMMO_PISTOL: splatSound.play(); return 8f; case AMMO_SMG: tinSound.play(1f); return 0.5f; default: tinSound.play(1f); return 0.3f; } }

    private boolean checkBulletHit(float ex, float ey, float bx, float by) {
        return Vector2.dst(ex + 32, ey + 32, bx + 4, by + 4) < 36f;
    }

    private void handlePlayerEnemyCollision() {
        if (player.dead || hitCooldown.isRunning()) return;
        if (player.getHp() <= 1) return;
        for (Enemy  e : enemies)  { if (checkPlayerHit(e.x, e.y)) { playerTakeDamage(); return; } }
        for (Enemy2 e : enemies2) { if (checkPlayerHit(e.x, e.y)) { playerTakeDamage(); return; } }
        for (Enemy3 e : enemies3) { if (checkPlayerHit(e.x, e.y)) { playerTakeDamage(); return; } }
    }

    private boolean checkPlayerHit(float ex, float ey) {
        return Vector2.dst(ex + 32, ey + 32, player.getCenterX(), player.getCenterY()) < 64f;
    }

    private void playerTakeDamage() {
        for (int i = 0; i < 50; i++)
            bloods.add(new BloodParticle(player.x + 32, player.y + 32, bloodTex));
        player.damage(1);
        woodSound.play(0.9f);
        hitCooldown.start(tickManager.getCurrentTick());

        if (player.dead && !deathTimerStarted) {
            deathTimerStarted = true;
            deathTimer.start(tickManager.getCurrentTick());
        }
    }

    private void handlePlayerBloodCollision() {
        if (player.dead || hitCooldown.isRunning()) return;
        for (BloodParticle b : bloods) {
            if (b.dead) continue;
            float dist = Vector2.dst(b.x + 4, b.y + 4, player.getCenterX(), player.getCenterY());
            if (dist < 36f) {
                isSlowed = true;
                slowdownTimer.start(tickManager.getCurrentTick());
                player.slowDown(300);
                b.dead = true;
                popSound.play(0.3f);
            }
        }
    }

    /** Tüm düşmanlar öldü mü? (aktif savaş fazında) */
    private void checkAllDeadForPhase() {
        if (!isShootingPhase() || killDone) return;
        if (enemies.size == 0 && enemies2.size == 0 && enemies3.size == 0) return;

        boolean allDead = true;
        for (Enemy  e : enemies)  if (!e.dead)  { allDead = false; break; }
        if (allDead) for (Enemy2 e : enemies2) if (!e.dead) { allDead = false; break; }
        if (allDead) for (Enemy3 e : enemies3) if (!e.dead) { allDead = false; break; }

        if (allDead) {
            killDone = true;
            killDelayTimer.start(tickManager.getCurrentTick());
        }
    }

    // ── Bayonet ───────────────────────────────────────────────────────────────

    private int useBayonet() {
        int killedCount = 0;
        sliceSound.play(1.2f);
        showBayonetAnim = true;
        bayonetAnimTime = 0f;
        bayonetCooldown.start(tickManager.getCurrentTick());

        for (Enemy e : enemies) {
            if (!e.dead && isInBayonetRange(e.x, e.y)) {
                e.dead = true; createBloodEffect(e.x, e.y); popSound.play(1f); killedCount++;
            }
        }
        for (Enemy2 e : enemies2) {
            if (!e.dead && isInBayonetRange(e.x, e.y)) {
                e.dead = true; createBloodEffect(e.x, e.y); popSound.play(1f); killedCount++;
            }
        }
        for (Enemy3 e : enemies3) {
            if (!e.dead && isInBayonetRange(e.x, e.y)) {
                e.dead = true; createBloodEffect(e.x, e.y); popSound.play(1f); killedCount++;
            }
        }
        return killedCount;
    }

    private boolean isInBayonetRange(float ex, float ey) {
        return Vector2.dst(ex + 32, ey + 32, player.getCenterX(), player.getCenterY()) < BAYONET_RANGE;
    }

    // ── Efektler ──────────────────────────────────────────────────────────────

    private void createBloodEffect(float x, float y) {
        for (int i = 0; i < 8; i++) bloods.add(new BloodParticle(x + 32, y + 32, bloodTex));
    }

    private void createTozEffect(float x, float y) {
        for (int i = 0; i < 8; i++) tozlar.add(new toz(x + 32, y + 32, tozTex));
    }

    // ── Tutorial faz mantığı ─────────────────────────────────────────────────

    private void handlePhaseLogic() {
        if (killDone) return; // killDelayTimer bekliyor

        switch (phase) {
            // ── Bilgi ekranları (waitInput = true) ──
            case 0:
            case 1:
                waitInput = true;
                break;

            // ── Tip 1 tanıtım + düşman spawn ──
            case 2:
                waitInput = true;
                if (enemies.isEmpty() && enemies2.isEmpty() && enemies3.isEmpty()) {
                    player.setWeapon(new Weapons(Weapons.WeaponType.SMG));
                    spawnEnemy1At(WORLD_WIDTH / 2f + 150, WORLD_HEIGHT / 2f + 160);
                }
                break;
            case 3: // Savaş
                waitInput = false;
                if (enemies.isEmpty()) spawnEnemy1At(WORLD_WIDTH / 2f + 150, WORLD_HEIGHT / 2f + 160);
                break;
            case 4:
                waitInput = true;
                clearEnemiesAndEffects();
                break;

            // ── Tip 2 tanıtım ──
            case 5:
                waitInput = true;
                if (enemies.isEmpty() && enemies2.isEmpty()) {
                    player.setWeapon(new Weapons(Weapons.WeaponType.SHOTGUN));
                    spawnEnemy2At(WORLD_WIDTH / 2f + 150, WORLD_HEIGHT / 2f + 160);
                }
                break;
            case 6:
                waitInput = false;
                if (enemies2.isEmpty()) spawnEnemy2At(WORLD_WIDTH / 2f + 150, WORLD_HEIGHT / 2f + 160);
                break;
            case 7:
                waitInput = true;
                clearEnemiesAndEffects();
                break;

            // ── Tip 3 tanıtım ──
            case 8:
                waitInput = true;
                if (enemies3.isEmpty()) {
                    player.setWeapon(new Weapons(Weapons.WeaponType.PISTOL));
                    spawnEnemy3At(WORLD_WIDTH / 2f + 150, WORLD_HEIGHT / 2f + 160);
                }
                break;
            case 9:
                waitInput = false;
                if (enemies3.isEmpty()) spawnEnemy3At(WORLD_WIDTH / 2f + 150, WORLD_HEIGHT / 2f + 160);
                break;
            case 10:
                waitInput = true;
                clearEnemiesAndEffects();
                break;

            // ── Bayonet tanıtım ──
            case 11:
                waitInput = true;
                clearEnemiesAndEffects();
                break;
            case 12: // Bayonet savaşı (3 düşman aynı anda)
                waitInput = false;
                if (enemies.isEmpty() && enemies2.isEmpty() && enemies3.isEmpty() && !killDone) {
                    float cx = WORLD_WIDTH  / 2f;
                    float cy = WORLD_HEIGHT / 2f;
                    spawnEnemy1At(cx - 140, cy + 180);
                    spawnEnemy2At(cx,        cy + 200);
                    spawnEnemy3At(cx + 140,  cy + 180);
                }
                break;

            // ── Özet ──
            case 13:
                waitInput = true;
                clearEnemiesAndEffects();
                break;
        }
    }

    private void handleTutorialInput() {
        if (player.dead) return;

        Controller c = getGamepad();
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_A, prevButtonA);

        if (waitInput && confirm && !killDone) {
            clearEnemiesAndEffects();
            advancePhase();
        }
    }

    private void advancePhase() {
        phase++;
        phaseTime = 0f;
        waitInput = false;
        killDone  = false;

        if (phase > 13) {
            game.setScreen(new MainMenuScreen(game));
        }
    }

    // ── Spawn yardımcıları ────────────────────────────────────────────────────

    private void spawnEnemy1At(float x, float y) { enemies.add(new Enemy(x, y)); }
    private void spawnEnemy2At(float x, float y) { enemies2.add(new Enemy2(x, y)); }
    private void spawnEnemy3At(float x, float y) { enemies3.add(new Enemy3(x, y)); }

    private void clearEnemiesAndEffects() {
        enemies.clear();
        enemies2.clear();
        enemies3.clear();
        bullets.clear();
        bloods.clear();
        tozlar.clear();
        killDone = false;
    }

    // ── Temizlik ──────────────────────────────────────────────────────────────

    private void cleanupDeadObjects() {
        for (int i = enemies.size  - 1; i >= 0; i--) if (enemies.get(i).dead)  enemies.removeIndex(i);
        for (int i = enemies2.size - 1; i >= 0; i--) if (enemies2.get(i).dead) enemies2.removeIndex(i);
        for (int i = enemies3.size - 1; i >= 0; i--) if (enemies3.get(i).dead) enemies3.removeIndex(i);
        for (int i = bullets.size  - 1; i >= 0; i--) if (bullets.get(i).dead)  bullets.removeIndex(i);
        for (int i = bloods.size   - 1; i >= 0; i--) if (bloods.get(i).dead)   bloods.removeIndex(i);
        for (int i = tozlar.size   - 1; i >= 0; i--) if (tozlar.get(i).dead)   tozlar.removeIndex(i);
    }

    //RENDER

    private void renderGame() {
        mapRenderer.setView(camera);
        mapRenderer.getBatch().setShader(mapShader);
        if (mapShader.hasUniform("u_time")) mapShader.setUniformf("u_time", shaderTime);
        mapRenderer.render();
        mapRenderer.getBatch().setShader(null);



        batch.setProjectionMatrix(camera.combined);

        // Bayonet animasyon
        if (showBayonetAnim) {
            batch.begin();
            float bx = 16f, by = 32f, orbit = 60f;
            float rot   = (bayonetAnimTime / 0.3f) * 360f * 1.5f;
            float alpha = 1f - (bayonetAnimTime / 0.3f);
            batch.setColor(1f, 1f, 1f, alpha);
            float drawX = player.getCenterX() + MathUtils.cosDeg(rot) * orbit;
            float drawY = player.getCenterY() + MathUtils.sinDeg(rot) * orbit;
            batch.draw(bayonetTex,
                    drawX - bx / 2, drawY - by / 2,
                    bx / 2, by / 2, bx, by, 1f, 1f,
                    rot - 90f,
                    0, 0, bayonetTex.getWidth(), bayonetTex.getHeight(),
                    false, false);
            batch.setColor(Color.WHITE);
            batch.end();
        }

        // Efektler & mermiler & oyuncu
        batch.setShader(null);
        batch.begin();
        player.draw(batch);
        player.drawGun(batch, camera);
        for (Bullet      b : bullets) batch.draw(b.getTexture(), b.x, b.y);
        for (BloodParticle b : bloods) batch.draw(bloodTex, b.x, b.y);
        for (toz         t : tozlar)  batch.draw(tozTex, t.x, t.y);
        batch.end();

        // Düşmanlar — shader ile
        batch.begin();
        batch.setShader(enemyShader);
        if (enemyShader != null && enemyShader.isCompiled()) {
            if (enemyShader.hasUniform("u_time")) enemyShader.setUniformf("u_time", shaderTime);
        }
        for (Enemy  e : enemies)  batch.draw(enemyTex,  e.x, e.y);
        for (Enemy2 e : enemies2) batch.draw(enemy2Tex, e.x, e.y);
        for (Enemy3 e : enemies3) batch.draw(enemy3Tex, e.x, e.y);
        batch.end();
        batch.setShader(null);

        // Can barları (shape renderer, world koordinat)
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Enemy  e : enemies)  drawEnemyBar(e.x, e.y, e.hp, e.maxHp);
        for (Enemy2 e : enemies2) drawEnemyBar(e.x, e.y, e.hp, e.maxHp);
        for (Enemy3 e : enemies3) drawEnemyBar(e.x, e.y, e.hp, e.maxHp);
        shapeRenderer.end();

        // ── UI katmanı ──
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        renderHearts();
        renderHotbar();
        renderBayonetCooldownBar();
        renderTutorialText();
        batch.end();
    }

    // ── Can barı ─────────────────────────────────────────────────────────────

    private void drawEnemyBar(float x, float y, int hp, int maxHp) {
        if (hp <= 0) return;
        float bx = x, by = y + 68f, bw = 64f, bh = 7f;
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.85f);
        shapeRenderer.rect(bx, by, bw, bh);
        float ratio = MathUtils.clamp((float) hp / maxHp, 0f, 1f);
        shapeRenderer.setColor(ratio > 0.5f ? Color.valueOf("36d936") : Color.valueOf("d94f1a"));
        shapeRenderer.rect(bx, by, bw * ratio, bh);
    }

    // ── UI bileşenleri (GameScreen ile birebir) ────────────────────────────

    private void renderHearts() {
        int   maxHp    = 3;
        float heartSize = 64f;
        float startX   = 20f;
        float startY   = UI_HEIGHT - 58f;
        for (int i = 0; i < maxHp; i++) {
            Texture h;
            if (i < player.getHp()) {
                h = player.isRegenHeart(i) ? regenHeartTex : heartTex;
            } else {
                h = heartEmptyTex;
            }
            batch.draw(h, startX + i * (heartSize + 5), startY, heartSize, heartSize);
        }
    }

    private void renderHotbar() {
        Texture hotbar = Hotbar1;
        if (player != null && player.getWeapon() != null) {
            switch (player.getWeapon().getType()) {
                case PISTOL:  hotbar = Hotbar1; break;
                case SHOTGUN: hotbar = Hotbar2; break;
                case SMG:     hotbar = Hotbar3; break;
            }
        }
        float hw = 100, hh = 260;
        batch.draw(hotbar, UI_WIDTH + hw / 2f, UI_HEIGHT / 2f - hh / 2f, hw, hh);
    }

    private void renderBayonetCooldownBar() {
        // Bayonet bar için batch'i durdur, shape renderer aç
        batch.end();

        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float bw = 200, bh = 20;
        float bx = UI_WIDTH - bw - 20, by = 40;

        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(bx, by, bw, bh);

        float progress = bayonetCooldown.isRunning()
                ? bayonetCooldown.getProgress(tickManager.getCurrentTick())
                : 1.0f;

        if (progress >= 1.0f) {
            shapeRenderer.setColor(0.2f, 0.8f, 0.2f, 1.0f);
        } else {
            shapeRenderer.setColor(1.0f - progress * 0.5f, progress * 0.8f, 0.1f, 1.0f);
        }
        shapeRenderer.rect(bx, by, bw * progress, bh);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(bx, by, bw, bh);
        shapeRenderer.end();

        batch.begin();

        font.setColor(Color.WHITE);
        font.getData().setScale(0.5f);
        String label = game.bundle.get("game.ui.bayonet");
        if (bayonetCooldown.isRunning()) {
            label += game.bundle.format("game.ui.bayonet.cooldown",
                    bayonetCooldown.getRemainingSeconds(tickManager.getCurrentTick()));
        } else {
            label += game.bundle.get("game.ui.bayonet.ready");
        }
        font.draw(batch, label, bx, by + bh + 18);
        font.getData().setScale(1f);
    }

    // ── Tutorial metin / UI ───────────────────────────────────────────────────

    private void renderTutorialText() {
        float h = UI_HEIGHT;
        switch (phase) {
            case 0:
                drawLine(game.bundle.get("tutorial.phase0.title"), h - 60, 1.0f, new Color(1f, 0.4f, 0.2f, 1f));
                drawLine(game.bundle.get("tutorial.phase0.line1"), h - 90, 0.7f, Color.WHITE);
                drawLine(game.bundle.get("tutorial.phase0.line2"), h - 115, 0.65f, Color.WHITE);
                drawHint();
                break;
            case 1:
                drawLine(game.bundle.get("tutorial.phase1.line1"), h - 60, 0.75f, Color.WHITE);
                drawHint();
                break;
            case 2:
                drawLine(game.bundle.get("tutorial.phase2.line1"), h - 60, 0.72f, new Color(0.3f, 0.7f, 1f, 1f));
                drawHint();
                break;
            case 3:
                drawLine(game.bundle.get("tutorial.phase3.line1"), h - 60, 0.7f, new Color(0.3f, 0.7f, 1f, 1f));
                drawWeaponTag();
                break;
            case 4:
                drawLine(game.bundle.get("tutorial.phase4.line1"), h - 60, 0.72f, new Color(0.5f, 1f, 0.5f, 1f));
                drawDamageTable(1);
                drawHint();
                break;
            case 5:
                drawLine(game.bundle.get("tutorial.phase5.line1"), h - 60, 0.72f, new Color(1f, 0.6f, 0.2f, 1f));
                drawHint();
                break;
            case 6:
                drawLine(game.bundle.get("tutorial.phase6.line1"), h - 60, 0.7f, new Color(1f, 0.6f, 0.2f, 1f));
                drawWeaponTag();
                break;
            case 7:
                drawLine(game.bundle.get("tutorial.phase7.line1"), h - 60, 0.72f, new Color(0.5f, 1f, 0.5f, 1f));
                drawDamageTable(2);
                drawHint();
                break;
            case 8:
                drawLine(game.bundle.get("tutorial.phase8.line1"), h - 60, 0.72f, new Color(0.8f, 0.3f, 1f, 1f));
                drawHint();
                break;
            case 9:
                drawLine(game.bundle.get("tutorial.phase9.line1"), h - 60, 0.7f, new Color(0.9f, 0.9f, 0.3f, 1f));
                drawWeaponTag();
                break;
            case 10:
                drawLine(game.bundle.get("tutorial.phase10.line1"), h - 60, 0.72f, new Color(0.5f, 1f, 0.5f, 1f));
                drawDamageTable(3);
                drawHint();
                break;
            case 11:
                drawLine(game.bundle.get("tutorial.phase11.line1"), h - 60, 0.72f, new Color(0.9f, 0.5f, 0.5f, 1f));
                drawLine(game.bundle.get("tutorial.phase11.line2"), h - 85, 0.65f, Color.WHITE);
                drawHint();
                break;
            case 12:
                drawLine(game.bundle.get("tutorial.phase12.line1"), h - 60, 0.7f, new Color(0.9f, 0.5f, 0.5f, 1f));
                break;
            case 13:
                drawLine(game.bundle.get("tutorial.phase13.title"), h - 55, 1.0f, new Color(0.4f, 1f, 0.4f, 1f));
                drawSummary();
                drawHint();
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

    private void drawHint() {
        float blink = MathUtils.sin(phaseTime * 4f) * 0.25f + 0.75f;
        font.getData().setScale(0.58f);
        font.setColor(1f, 1f, 1f, blink);
        font.draw(batch, game.bundle.get("tutorial.hint"), 24, 28);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawWeaponTag() {
        String name;
        Color c;
        Weapons w = player.getWeapon();
        if (w == null) return;
        switch (w.getType()) {
            case SHOTGUN: name = game.bundle.get("tutorial.weapon.shotgun"); c = new Color(1f, 0.6f, 0.2f, 1f); break;
            case SMG:     name = game.bundle.get("tutorial.weapon.smg");     c = new Color(0.3f, 0.7f, 1f, 1f); break;
            default:      name = game.bundle.get("tutorial.weapon.pistol");  c = new Color(0.9f, 0.9f, 0.3f, 1f); break;
        }
        font.getData().setScale(0.7f);
        font.setColor(c);
        font.draw(batch, name, 24, 60);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawDamageTable(int enemyType) {
        String[] silahlar = {
                game.bundle.get("tutorial.damage.smg"),
                game.bundle.get("tutorial.damage.shotgun"),
                game.bundle.get("tutorial.damage.pistol")
        };
        String[] hasarlar;
        int bestIdx;
        switch (enemyType) {
            case 1:  hasarlar = new String[]{"15", "1",   "3"};  bestIdx = 0; break;
            case 2:  hasarlar = new String[]{"1",  "15",  "5"};  bestIdx = 1; break;
            default: hasarlar = new String[]{"0.5","0.3", "8"};  bestIdx = 2; break;
        }
        Color[] sc = {
                new Color(0.3f, 0.7f, 1f,  1f),
                new Color(1f,   0.6f, 0.2f,1f),
                new Color(0.9f, 0.9f, 0.3f,1f)
        };
        float startY = UI_HEIGHT - 115;
        font.getData().setScale(0.65f);
        String suffix = game.bundle.get("tutorial.damage.suffix");
        String best   = game.bundle.get("tutorial.damage.best");
        for (int i = 0; i < 3; i++) {
            font.setColor(sc[i]);
            font.draw(batch, silahlar[i], 24, startY - i * 22);
            font.setColor(i == bestIdx ? new Color(0.4f,1f,0.4f,1f) : new Color(0.6f,0.6f,0.6f,1f));
            font.draw(batch, hasarlar[i] + suffix + (i == bestIdx ? best : ""), 160, startY - i * 22);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void drawSummary() {
        String[][] rows = {
                {game.bundle.get("tutorial.summary.enemy1"),   game.bundle.get("tutorial.summary.smg"),          game.bundle.get("tutorial.summary.dmg15")},
                {game.bundle.get("tutorial.summary.enemy2"),   game.bundle.get("tutorial.summary.shotgun"),       game.bundle.get("tutorial.summary.dmg15")},
                {game.bundle.get("tutorial.summary.enemy3"),   game.bundle.get("tutorial.summary.pistol"),        game.bundle.get("tutorial.summary.dmg8")},
                {game.bundle.get("tutorial.summary.bayonet"),  game.bundle.get("tutorial.summary.bayonetAction"), game.bundle.get("tutorial.summary.melee")}
        };
        Color[] sc = {
                new Color(0.3f,0.7f,1f,1f),
                new Color(1f,0.6f,0.2f,1f),
                new Color(0.9f,0.9f,0.3f,1f),
                new Color(0.9f,0.5f,0.5f,1f)
        };
        float startY = UI_HEIGHT - 100;
        font.getData().setScale(0.7f);
        for (int i = 0; i < rows.length; i++) {
            float y = startY - i * 24;
            font.setColor(Color.WHITE);       font.draw(batch, rows[i][0],  24,  y);
            font.setColor(sc[i]);             font.draw(batch, rows[i][1], 100,  y);
            font.setColor(0.5f,0.9f,0.5f,1f);font.draw(batch, rows[i][2], 280,  y);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private Controller getGamepad() {
        return Controllers.getControllers().size > 0
                ? Controllers.getControllers().first()
                : null;
    }

    private boolean gamepadJustPressed(Controller c, int button, boolean prevState) {
        return c != null && c.getButton(button) && !prevState;
    }

    // ── Screen arayüzü ────────────────────────────────────────────────────────

    @Override
    public void show() {
        ShaderProgram.pedantic = false;
        enemyShader = new ShaderProgram(
                Gdx.files.internal("shaders/red.vsh"),
                Gdx.files.internal("shaders/red.fsh")
        );
        if (!enemyShader.isCompiled()) {
            Gdx.app.error("TutorialScreen", "Enemy shader hata: " + enemyShader.getLog());
            enemyShader = null;
        }
        map = new TmxMapLoader().load("tutorial.tmx");
        groundLayer      = (TiledMapTileLayer) map.getLayers().get(0);
        wallLayer        = (TiledMapTileLayer) map.getLayers().get("dk2");
        lowObstacleLayer = (TiledMapTileLayer) map.getLayers().get("dk3");
        mapRenderer = new OrthogonalTiledMapRenderer(map, 3f);

        mapShader = new ShaderProgram(
                Gdx.files.internal("shaders/map.vsh"),
                Gdx.files.internal("shaders/map.fsh")
        );
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        uiViewport.update(width, height, true);
        uiCamera.position.set(UI_WIDTH / 2f, UI_HEIGHT / 2f, 0);
        uiCamera.update();
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        if (batch         != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (enemyShader   != null) enemyShader.dispose();
        if (map != null) map.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (mapShader != null) mapShader.dispose();
    }
}