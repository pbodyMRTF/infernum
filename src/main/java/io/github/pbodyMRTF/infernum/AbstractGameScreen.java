package io.github.pbodyMRTF.infernum;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/**
 * Eskiden GameScreen'de tek parça olan her şeyin ORTAK kısmı burada yaşıyor:
 * render loop, collision/bullet/blood güncellemeleri, shader + harita yükleme,
 * HUD, ölüm/hasar akışı, dispose vs.
 *
 * Survival ve Campaign modları arasında farklı olan TEK şey "ne zaman/nasıl
 * düşman spawn olur" ve "seviye ne zaman biter" olduğu için bunlar abstract
 * hook'lar (onModeTick, checkLevelState, onEnemyKilled, onPlayerDied) olarak
 * dışarı açıldı. Alt sınıflar sadece bunları implemente ediyor.
 */
public abstract class AbstractGameScreen implements Screen {

    protected GameConfig config;
    protected final Jgame game;
    protected SpriteBatch batch;
    protected BitmapFont font;
    protected GameTickManager tickManager;
    protected LightingManager lighting;
    protected ShaderProgram shader1;
    protected ShaderProgram mapShader;
    protected ShaderProgram whiteShader;
    protected DamageFlashManager damageFlashManager = new DamageFlashManager();
    protected float shaderTime = 0f;
    protected ShapeRenderer shapeRenderer;

    protected Player player;

    protected Sound shootSound;
    protected Sound SmgSound;
    protected Sound ShotgunSound;
    protected Sound popSound;
    protected Sound woodSound;
    protected Sound sliceSound;
    protected Sound tinSound;
    protected Sound splatSound;
    private static float MasterSound = 1f;

    protected TiledMap map;
    protected OrthogonalTiledMapRenderer mapRenderer;
    protected OrthographicCamera camera;
    protected OrthographicCamera uiCamera;
    protected ExtendViewport viewport;
    protected ExtendViewport uiViewport;
    protected TiledMapTileLayer groundLayer;
    protected TiledMapTileLayer wallLayer;
    protected TiledMapTileLayer lowObstacleLayer;
    protected Texture enemyTex;
    protected Texture enemy2Tex;
    protected Texture enemy3Tex;
    protected Texture bloodTex;
    protected Texture tozTex;
    protected Texture heartTex;
    protected Texture heartEmptyTex;
    protected Texture regenHeartTex;
    protected Texture bayonetTex;

    protected Texture Hotbar1;
    protected Texture Hotbar2;
    protected Texture Hotbar3;

    protected Array<Bullet> bullets = new Array<>();
    protected Array<BloodParticle> bloods = new Array<>();
    protected Array<toz> tozlar = new Array<>();

    protected GameTickManager.TickTimer shootCooldown;
    protected GameTickManager.TickTimer hitCooldown;
    protected GameTickManager.TickTimer slowdownTimer;
    protected GameTickManager.TickTimer deathTimer;
    protected GameTickManager.TickTimer bayonetCooldown;

    protected int shootCooldownTicks = 16;
    protected int hitCooldownTicks = 16;
    protected int slowdownTicks = 40;
    protected int deathDelayTicks = 20;
    protected int bayonetCooldownTicks = 60;

    protected static int score = 0;
    protected boolean isSlowed = false;
    protected boolean deathTimerStarted = false;
    protected float bayonetAnimTime = 0f;
    protected boolean showBayonetAnim = false;

    protected String difficulty;

    protected static final float WORLD_WIDTH  = 1024f;
    protected static final float WORLD_HEIGHT = 768f;
    protected static final float UI_WIDTH     = 1024f;
    protected static final float UI_HEIGHT    = 768f;
    protected static final float BAYONET_RANGE = 150f;

    protected EntityManager entityManager = new EntityManager();
    protected CollisionHandler collisionHandler;
    protected ShootingHandler shootingHandler;
    protected HUD hud;
    protected Renderer renderer;

    protected AbstractGameScreen(final Jgame game) {
        this.game     = game;
        batch         = new SpriteBatch();
        font          = game.getFont(Jgame.FONT_SIZE_32);
        shapeRenderer = new ShapeRenderer();

        loadConfig();
        this.difficulty = resolveDifficulty(config);
        applyDifficultySettings();

        camera   = new OrthographicCamera();
        viewport = new ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);

        uiCamera   = new OrthographicCamera();
        uiViewport = new ExtendViewport(UI_WIDTH, UI_HEIGHT, uiCamera);
        uiCamera.position.set(UI_WIDTH / 2, UI_HEIGHT / 2, 0);
        uiCamera.update();

        tickManager = new GameTickManager();
        tickManager.addListener(new GameTickManager.TickListener() {
            @Override
            public void onTick(int currentTick) {
                handleTick(currentTick);
            }
        });

        shootCooldown   = new GameTickManager.TickTimer(shootCooldownTicks);
        hitCooldown     = new GameTickManager.TickTimer(hitCooldownTicks);
        slowdownTimer   = new GameTickManager.TickTimer(slowdownTicks);
        deathTimer      = new GameTickManager.TickTimer(deathDelayTicks);
        bayonetCooldown = new GameTickManager.TickTimer(bayonetCooldownTicks);

        loadAssets();
        spawnPlayer();

        hud = new HUD(font, shapeRenderer, heartTex, heartEmptyTex, regenHeartTex,
                Hotbar1, Hotbar2, Hotbar3, game, entityManager, camera, uiCamera);

        collisionHandler = new CollisionHandler(entityManager, bullets, bloods, player,
                popSound, tinSound, splatSound, damageFlashManager,
                new CollisionHandler.CollisionListener() {
                    @Override
                    public void onEnemyKilled(Entity e) {
                        createBloodEffect(e.getX(), e.getY());
                        if (e instanceof Enemy2) createTozEffect(e.getX(), e.getY());
                        popSound.play(0.7f * MasterSound);
                        AbstractGameScreen.this.onEnemyKilled(e);
                    }
                    @Override
                    public void onPlayerDamaged() {
                        playerTakeDamage();
                    }
                    @Override
                    public void onPlayerSlowed(BloodParticle b) {
                        isSlowed = true;
                        slowdownTimer.start(tickManager.getCurrentTick());
                        player.slowDown(300);
                    }
                });

        shootingHandler = new ShootingHandler(player, bullets, shootSound, SmgSound, ShotgunSound,
                camera, new ShootingHandler.ShootingListener() {
            @Override
            public void onShoot(GameTickManager.TickTimer newCooldown) {
                shootCooldown = newCooldown;
            }
        });
    }

    // ---- Alt sınıfların doldurması gereken kısımlar ----------------------

    /** "flape.tmx" gibi hangi .tmx dosyasının yükleneceği. */
    protected abstract String getMapFileName();

    /** config.json'daki zorluk mu, yoksa level'a özel bir zorluk mu kullanılacak. */
    protected abstract String resolveDifficulty(GameConfig config);

    /** Her tick'te çağrılır: survival'da spawnManager, campaign'de wave scripti. */
    protected abstract void onModeTick(int currentTick);

    /** Düşman öldüğünde skor/objective güncellemesi (blood/pop sesi zaten ortak yapılıyor). */
    protected abstract void onEnemyKilled(Entity e);

    /** Oyuncu öldüğünde (deathTimer bitince) hangi ekrana gidileceği. */
    protected abstract void onPlayerDied();

    /** Ölüm dışındaki bitiş koşulları (seviye tamamlandı vb.) her render'da kontrol edilir. */
    protected abstract void checkLevelState();

    // ------------------------------------------------------------------

    protected void applyDifficultySettings() {
        if ("ZOR".equals(difficulty)) {
            shootCooldownTicks = 16;
            hitCooldownTicks   = 10;
        } else if ("Ben Erlik Han'ım".equals(difficulty)) {
            shootCooldownTicks = 20;
            hitCooldownTicks   = 5;
        } else {
            shootCooldownTicks = 16;
            hitCooldownTicks   = 16;
        }
    }

    protected void loadAssets() {
        shootSound   = Assets.getSound(Assets.Sounds.SHOOT);
        ShotgunSound = Assets.getSound(Assets.Sounds.SHOTGUNSHOT);
        SmgSound     = Assets.getSound(Assets.Sounds.SMGSHOT);
        popSound     = Assets.getSound(Assets.Sounds.POP);
        woodSound    = Assets.getSound(Assets.Sounds.WOOD);
        sliceSound   = Assets.getSound(Assets.Sounds.SLICE);
        tinSound     = Assets.getSound(Assets.Sounds.TIN);
        splatSound   = Assets.getSound(Assets.Sounds.SPLAT);

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

    protected void loadConfig() {
        Json json = new Json();
        config = json.fromJson(GameConfig.class, Gdx.files.internal("config.json"));
    }

    /** Oyuncunun doğacağı yer moddan moda değişebilsin diye override edilebilir. */
    protected void spawnPlayer() {
        float x = 2036;
        float y = 1951;
        Texture playerTex = Assets.getTexture(Assets.Textures.PLAYER);
        player = new Player(x, y, playerTex, 0);
        player.setHitCooldown(hitCooldown);
        player.setBayonetCooldown(bayonetCooldown);
        player.setWeapon(new Weapons(Weapons.WeaponType.PISTOL));

        player.setBayonetCallback(new Player.BayonetCallback() {
            @Override
            public int onBayonetUse() {
                return getBayonetKills();
            }
        });
    }

    protected int getBayonetKills() {
        int killed = 0;
        sliceSound.play(1.2f * MasterSound);
        showBayonetAnim = true;
        bayonetAnimTime = 0f;
        bayonetCooldown.start(tickManager.getCurrentTick());
        for (Entity e : entityManager.getAll()) {
            if (!e.isDead() && isInBayonetRange(e.getX(), e.getY())) {
                e.setDead(true);
                createBloodEffect(e.getX(), e.getY());
                popSound.play(1f * MasterSound);
                onEnemyKilled(e);
                killed++;
            }
        }
        return killed;
    }

    protected boolean isInBayonetRange(float ex, float ey) {
        float dist = Vector2.dst(ex + 32, ey + 32, player.getCenterX(), player.getCenterY());
        return dist < BAYONET_RANGE;
    }

    private void handleTick(int currentTick) {
        onModeTick(currentTick);
        checkAndStopTimers(currentTick);
    }

    protected void checkAndStopTimers(int currentTick) {
        if (deathTimer.isRunning() && deathTimer.isFinished(currentTick)) {
            onPlayerDied();
        }

        if (slowdownTimer.isRunning() && slowdownTimer.isFinished(currentTick)) {
            player.resetSpeed();
            isSlowed = false;
            slowdownTimer.stop();
        }

        if (player.isWeaponJustChanged()) shootCooldown.stop();

        if (shootCooldown.isRunning() && shootCooldown.isFinished(currentTick)) shootCooldown.stop();
        if (hitCooldown.isRunning()   && hitCooldown.isFinished(currentTick))   hitCooldown.stop();
        if (bayonetCooldown.isRunning() && bayonetCooldown.isFinished(currentTick)) bayonetCooldown.stop();
    }

    protected Texture takeScreenshot() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    @Override
    public void render(float delta) {
        if (renderer == null || groundLayer == null) return;
        tickManager.update(delta);
        shaderTime += delta;

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) Gdx.app.exit();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Texture snapshot = takeScreenshot();
            game.setScreen(new PauseScreen(game, this, snapshot));
            return;
        }

        player.update(delta, wallLayer, lowObstacleLayer);
        float aimAngle = player.getAngleToMouse(camera);
        lighting.updateConeLight(player.getCenterX(), player.getCenterY(), aimAngle);
        shootingHandler.handle(shootCooldown.isRunning(), tickManager.getCurrentTick());
        updateBloodParticles(delta);
        updateToz(delta);
        updateBayonetAnim(delta);
        updateBullets(delta);
        updateEnemies(delta);
        handleCollisions();
        cleanupDeadObjects();
        renderGame();
        checkLevelState();

        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            CollisionExportUtil.export(wallLayer, lowObstacleLayer, "collision_flape.txt");
        }
    }

    protected void updateBayonetAnim(float delta) {
        if (showBayonetAnim) {
            bayonetAnimTime += delta;
            if (bayonetAnimTime >= 0.3f) showBayonetAnim = false;
        }
    }

    protected void updateBloodParticles(float delta) {
        for (BloodParticle blood : bloods) blood.update(delta);
    }

    protected void updateToz(float delta) {
        for (toz toz : tozlar) toz.update(delta);
    }

    protected void updateBullets(float delta) {
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;
        for (Bullet b : bullets) b.update(delta, wallLayer, mapWidth, mapHeight);
    }

    protected void updateEnemies(float delta) {
        entityManager.updateAll(delta, player.x, player.y);
    }

    protected void handleCollisions() {
        collisionHandler.handleAll(hitCooldown.isRunning(), tickManager.getCurrentTick());
    }

    protected void createBloodEffect(float x, float y) {
        for (int i = 0; i < 8; i++) bloods.add(new BloodParticle(x + 32, y + 32, bloodTex));
    }

    protected void createTozEffect(float x, float y) {
        for (int i = 0; i < 8; i++) tozlar.add(new toz(x + 32, y + 32, tozTex));
    }

    protected void playerTakeDamage() {
        for (int i = 0; i < 50; i++) bloods.add(new BloodParticle(player.x + 32, player.y + 32, bloodTex));

        player.damage(1);
        woodSound.play(0.9f * MasterSound);
        hitCooldown.start(tickManager.getCurrentTick());

        if (player.dead && !deathTimerStarted) {
            deathTimerStarted = true;
            deathTimer.start(tickManager.getCurrentTick());
        }
    }

    protected void cleanupDeadObjects() {
        entityManager.cleanup();
        for (int i = bloods.size - 1; i >= 0; i--) if (bloods.get(i).dead) bloods.removeIndex(i);
        for (int i = tozlar.size - 1; i >= 0; i--) if (tozlar.get(i).dead) tozlar.removeIndex(i);
        for (int i = bullets.size - 1; i >= 0; i--) if (bullets.get(i).dead) bullets.removeIndex(i);
    }

    protected void renderGame() {
        renderer.render(shaderTime, player, bullets, bloods, tozlar,
                showBayonetAnim, bayonetAnimTime, isSlowed,
                slowdownTimer.getRemainingSeconds(tickManager.getCurrentTick()),
                score, bayonetCooldown, tickManager.getCurrentTick());
    }

    @Override
    public void show() {
        ShaderProgram.pedantic = false;

        shader1 = new ShaderProgram(
                Gdx.files.internal("shaders/red.vsh"),
                Gdx.files.internal("shaders/red.fsh")
        );
        whiteShader = new ShaderProgram(
                Gdx.files.internal("shaders/white.vsh"),
                Gdx.files.internal("shaders/white.fsh")
        );
        mapShader = new ShaderProgram(
                Gdx.files.internal("shaders/map.vsh"),
                Gdx.files.internal("shaders/map.fsh")
        );

        if (!shader1.isCompiled()) throw new GdxRuntimeException("Shader1 hata: " + shader1.getLog());
        if (!mapShader.isCompiled()) throw new GdxRuntimeException("MapShader hata: " + mapShader.getLog());
        if (!whiteShader.isCompiled()) throw new GdxRuntimeException("whiteShader hata:" + whiteShader.getLog());

        TmxMapLoader loader = new TmxMapLoader();
        map = loader.load(getMapFileName());

        groundLayer = (TiledMapTileLayer) map.getLayers().get(0);
        wallLayer = (TiledMapTileLayer) map.getLayers().get("dk2");
        lowObstacleLayer = (TiledMapTileLayer) map.getLayers().get("dk3");
        mapRenderer = new OrthogonalTiledMapRenderer(map, 3f);

        onMapLoaded();

        lighting = new LightingManager();
        WallBodyBuilder.build(lighting.getWorld(), wallLayer);

        renderer = new Renderer(batch, camera, uiCamera, viewport, mapRenderer,
                shader1, mapShader, whiteShader, groundLayer, entityManager,
                bloodTex, tozTex, bayonetTex, hud, damageFlashManager, lighting);
    }

    /** groundLayer/wallLayer hazır olduktan hemen sonra çağrılır (ör. SpawnManager.setGroundLayer). */
    protected void onMapLoaded() {
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        uiViewport.update(width, height, true);
        uiCamera.position.set(UI_WIDTH / 2, UI_HEIGHT / 2, 0);
        uiCamera.update();
    }

    public static int getScore() {
        return score;
    }
    public static void setScore(int a) {
        score = a;
    }
    public static void setMasterSound(float f) {
        MasterSound = f;
    }
    public static float getMasterSound() {
        return MasterSound;
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        if (shader1       != null) shader1.dispose();
        if (mapShader     != null) mapShader.dispose();
        if (batch         != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (mapRenderer   != null) mapRenderer.dispose();
        if (map           != null) map.dispose();
    }
}