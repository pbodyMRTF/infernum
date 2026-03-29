import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class GameScreen implements Screen {
    GameConfig config;
    private final Jgame game;
    private SpriteBatch batch;
    private BitmapFont font;
    private GameTickManager tickManager;
    private ShaderProgram shader1;
    private ShaderProgram mapShader;
    private float shaderTime = 0f;
    private ShapeRenderer shapeRenderer;

    private Player player;

    private Sound shootSound;
    private Sound SmgSound;
    private Sound ShotgunSound;
    private Sound popSound;
    private Sound woodSound;
    private Sound sliceSound;
    private Sound tinSound;
    private Sound splatSound;

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;
    private ExtendViewport viewport;
    private ExtendViewport uiViewport;
    private TiledMapTileLayer groundLayer;
    private TiledMapTileLayer wallLayer;
    private TiledMapTileLayer lowObstacleLayer;
    private Texture enemyTex;
    private Texture enemy2Tex;
    private Texture enemy3Tex;
    private Texture bloodTex;
    private Texture tozTex;
    private Texture heartTex;
    private Texture heartEmptyTex;
    private Texture regenHeartTex;
    private Texture bayonetTex;

    private Texture Hotbar1;
    private Texture Hotbar2;
    private Texture Hotbar3;

    private Array<Bullet> bullets = new Array<>();
    private Array<BloodParticle> bloods = new Array<>();
    private Array<toz> tozlar = new Array<>();

    private SpawnManager spawnManager;

    private GameTickManager.TickTimer shootCooldown;
    private GameTickManager.TickTimer hitCooldown;
    private GameTickManager.TickTimer slowdownTimer;
    private GameTickManager.TickTimer deathTimer;
    private GameTickManager.TickTimer bayonetCooldown;

    private float baseSpawnInterval;
    private float minSpawnInterval = 0.01f;
    private int shootCooldownTicks = 16;
    private int hitCooldownTicks = 16;
    private int slowdownTicks = 40;
    private int deathDelayTicks = 20;
    private int bayonetCooldownTicks = 60;

    private int score = 0;
    private boolean isSlowed = false;
    private boolean deathTimerStarted = false;
    private float bayonetAnimTime = 0f;
    private boolean showBayonetAnim = false;

    private String difficulty;

    private int fpsTickCounter = 0;

    private static final float WORLD_WIDTH  = 1024f;
    private static final float WORLD_HEIGHT = 768f;
    private static final float UI_WIDTH     = 1024f;
    private static final float UI_HEIGHT    = 768f;
    private static final float BAYONET_RANGE = 150f;

    private EntityManager entityManager = new EntityManager();
    private CollisionHandler collisionHandler;
    private ShootingHandler shootingHandler;
    private HUD hud;
    public GameScreen(final Jgame game) {
        this.game  = game;
        batch         = new SpriteBatch();
        font          = game.getFont(Jgame.FONT_SIZE_32);
        shapeRenderer = new ShapeRenderer();

        loadConfig();
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

        spawnManager = new SpawnManager(entityManager, enemyTex, enemy2Tex, enemy3Tex,
                game.rnd, baseSpawnInterval, minSpawnInterval);

        spawnPlayer();
        hud = new HUD(font, shapeRenderer, heartTex, heartEmptyTex, regenHeartTex,
                Hotbar1, Hotbar2, Hotbar3, game);
        collisionHandler = new CollisionHandler(entityManager, bullets, bloods, player,
                popSound, tinSound, splatSound,
                new CollisionHandler.CollisionListener() {
                    @Override
                    public void onEnemyKilled(Entity e) {
                        createBloodEffect(e.getX(), e.getY());
                        if (e instanceof Enemy2) createTozEffect(e.getX(), e.getY());
                        popSound.play(0.7f);
                        score++;
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
                        popSound.play(0.3f);
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

    private void applyDifficultySettings() {
        if (difficulty.equals("ZOR")) {
            baseSpawnInterval  = 0.8f;
            minSpawnInterval   = 0.3f;
            shootCooldownTicks = 16;
            hitCooldownTicks   = 10;
        } else if (difficulty.equals("Ben Erlik Han'ım")) {
            baseSpawnInterval  = 0.01f;
            minSpawnInterval   = 0f;
            shootCooldownTicks = 20;
            hitCooldownTicks   = 5;
        } else {
            baseSpawnInterval  = 1.2f;
            minSpawnInterval   = 0.6f;
            shootCooldownTicks = 16;
            hitCooldownTicks   = 16;
        }
    }

    private void loadAssets() {
        shootSound   = Assets.getSound(Assets.Sounds.SHOOT);
        ShotgunSound = Assets.getSound(Assets.Sounds.SHOTGUNSHOT);
        SmgSound     = Assets.getSound(Assets.Sounds.SMGSHOT);
        popSound     = Assets.getSound(Assets.Sounds.POP);
        woodSound    = Assets.getSound(Assets.Sounds.WOOD);
        sliceSound   = Assets.getSound(Assets.Sounds.SLICE);
        tinSound     = Assets.getSound(Assets.Sounds.TIN);
        splatSound   = Assets.getSound(Assets.Sounds.SPLAT);


        enemyTex     = Assets.getTexture(Assets.Textures.ENEMY);
        enemy2Tex    = Assets.getTexture(Assets.Textures.ENEMY2);
        enemy3Tex    = Assets.getTexture(Assets.Textures.ENEMY3);
        bloodTex     = Assets.getTexture(Assets.Textures.BLOOD);
        tozTex       = Assets.getTexture(Assets.Textures.TOZ);
        heartTex     = Assets.getTexture(Assets.Textures.HEART);
        heartEmptyTex = Assets.getTexture(Assets.Textures.HEART_EMPTY);
        regenHeartTex = Assets.getTexture(Assets.Textures.REGEN_KALP);
        bayonetTex   = Assets.getTexture(Assets.Textures.BAYONET);

        Hotbar1 = Assets.getTexture(Assets.Textures.HOTBAR1);
        Hotbar2 = Assets.getTexture(Assets.Textures.HOTBAR2);
        Hotbar3 = Assets.getTexture(Assets.Textures.HOTBAR3);
    }

    private void loadConfig() {
        Json json = new Json();
        config          = json.fromJson(GameConfig.class, Gdx.files.internal("config.json"));

        this.difficulty  = config.difficulty;
    }
    private void spawnPlayer() {
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

    private int getBayonetKills() {
        int killed = 0;
        sliceSound.play(1.2f);
        showBayonetAnim = true;
        bayonetAnimTime = 0f;
        bayonetCooldown.start(tickManager.getCurrentTick());
        for (Entity e : entityManager.getAll()) {
            if (!e.isDead() && isInBayonetRange(e.getX(), e.getY())) {
                e.setDead(true);
                createBloodEffect(e.getX(), e.getY());
                popSound.play(1f);
                score++;
                killed++;
            }
        }
        return killed;
    }
    private boolean isInBayonetRange(float ex, float ey) {
        float dist = Vector2.dst(ex + 32, ey + 32, player.getCenterX(), player.getCenterY());
        return dist < BAYONET_RANGE;
    }

    private void handleTick(int currentTick) {
        fpsTickCounter++;
        if (fpsTickCounter >= 20) fpsTickCounter = 0;
        spawnManager.handleEnemySpawn(currentTick, score, tickManager);
        checkAndStopTimers(currentTick);
    }

    private void checkAndStopTimers(int currentTick) {
        if (deathTimer.isRunning() && deathTimer.isFinished(currentTick)) {
            game.setScreen(new MainMenuScreen(game));
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
    @Override
    public void render(float delta) {
        tickManager.update(delta);
        shaderTime += delta;

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) Gdx.app.exit();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        player.update(delta, wallLayer, lowObstacleLayer);
        shootingHandler.handle(shootCooldown.isRunning(), tickManager.getCurrentTick());
        updateBloodParticles(delta);
        updateToz(delta);
        updateBayonetAnim(delta);
        updateBullets(delta);
        updateEnemies(delta);
        handleCollisions();
        cleanupDeadObjects();
        renderGame();
    }
    private void updateBayonetAnim(float delta) {
        if (showBayonetAnim) {
            bayonetAnimTime += delta;
            if (bayonetAnimTime >= 0.3f) showBayonetAnim = false;
        }
    }
    private void updateBloodParticles(float delta) {
        for (BloodParticle blood : bloods) blood.update(delta);
    }

    private void updateToz(float delta) {
        for (toz toz : tozlar) toz.update(delta);
    }

    private void updateBullets(float delta) {
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;
        for (Bullet b : bullets) b.update(delta, wallLayer, mapWidth, mapHeight);
    }

    private void updateEnemies(float delta) {
        entityManager.updateAll(delta, player.x, player.y);
    }

    private void handleCollisions() {
        collisionHandler.handleAll(hitCooldown.isRunning());
    }

    private void createBloodEffect(float x, float y) {
        for (int i = 0; i < 8; i++) bloods.add(new BloodParticle(x + 32, y + 32, bloodTex));
    }

    private void createTozEffect(float x, float y) {
        for (int i = 0; i < 8; i++) tozlar.add(new toz(x + 32, y + 32, tozTex));
    }

    private void playerTakeDamage() {
        for (int i = 0; i < 50; i++) bloods.add(new BloodParticle(player.x + 32, player.y + 32, bloodTex));

        player.damage(1);
        woodSound.play(0.9f);
        hitCooldown.start(tickManager.getCurrentTick());

        if (player.dead && !deathTimerStarted) {
            deathTimerStarted = true;
            deathTimer.start(tickManager.getCurrentTick());
        }
    }


    private void cleanupDeadObjects() {
        entityManager.cleanup();
        for (int i = bloods.size - 1; i >= 0; i--) if (bloods.get(i).dead) bloods.removeIndex(i);
        for (int i = tozlar.size - 1; i >= 0; i--) if (tozlar.get(i).dead) tozlar.removeIndex(i);
        for (int i = bullets.size - 1; i >= 0; i--) if (bullets.get(i).dead) bullets.removeIndex(i);
    }

    private void renderGame() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.position.set(player.getCenterX(), player.getCenterY(), 0);

        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        camera.position.x = MathUtils.clamp(camera.position.x, viewport.getWorldWidth()  / 2, mapWidth  - viewport.getWorldWidth()  / 2);
        camera.position.y = MathUtils.clamp(camera.position.y, viewport.getWorldHeight() / 2, mapHeight - viewport.getWorldHeight() / 2);

        camera.update();

        renderer.setView(camera);
        renderer.getBatch().setShader(mapShader);
        if (mapShader.hasUniform("u_time"))       mapShader.setUniformf("u_time", shaderTime);
        if (mapShader.hasUniform("u_resolution")) mapShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderer.render();
        renderer.getBatch().setShader(null);

        if (showBayonetAnim) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();

            float bicakBoyutuX    = 16f;
            float bicakBoyutuY    = 32f;
            float yorungeUzakligi = 60f;
            float rotation        = (bayonetAnimTime / 0.3f) * 360f * 1.5f;
            float alpha           = 1f - (bayonetAnimTime / 0.3f);
            batch.setColor(1f, 1f, 1f, alpha);

            float drawX    = player.getCenterX() + MathUtils.cosDeg(rotation) * yorungeUzakligi;
            float drawY    = player.getCenterY() + MathUtils.sinDeg(rotation) * yorungeUzakligi;
            float sabitAci = -90f;

            batch.draw(bayonetTex,
                    drawX - (bicakBoyutuX / 2), drawY - (bicakBoyutuY / 2),
                    bicakBoyutuX / 2, bicakBoyutuY / 2,
                    bicakBoyutuX, bicakBoyutuY,
                    1f, 1f,
                    rotation + sabitAci,
                    0, 0, bayonetTex.getWidth(), bayonetTex.getHeight(),
                    false, false);

            batch.setColor(Color.WHITE);
            batch.end();
        }

        batch.setProjectionMatrix(camera.combined);
        batch.setShader(null);
        batch.begin();
        player.draw(batch);
        player.drawGun(batch, camera);
        for (Bullet      b : bullets) batch.draw(b.getTexture(), b.x, b.y);
        for (BloodParticle b : bloods) batch.draw(bloodTex, b.x, b.y);
        for (toz         t : tozlar)  batch.draw(tozTex, t.x, t.y);
        batch.end();

        batch.begin();
        batch.setShader(shader1);
        if (shader1.hasUniform("u_time")) shader1.setUniformf("u_time", shaderTime);
        for (Entity e : entityManager.getAll()) {
            if (!e.isDead()) batch.draw(e.getTexture(), e.getX(), e.getY());
        }
        batch.end();

        batch.setShader(null);
        batch.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        hud.render(batch, player, isSlowed,
                slowdownTimer.getRemainingSeconds(tickManager.getCurrentTick()),
                score, bayonetCooldown, tickManager.getCurrentTick());
        batch.end();
    }

    @Override
    public void show() {
        ShaderProgram.pedantic = false;

        shader1 = new ShaderProgram(
                Gdx.files.internal("shaders/red.vsh"),
                Gdx.files.internal("shaders/red.fsh")
        );
        mapShader = new ShaderProgram(
                Gdx.files.internal("shaders/map.vsh"),
                Gdx.files.internal("shaders/map.fsh")
        );

        if (!shader1.isCompiled())   throw new GdxRuntimeException("Shader1 hata: "   + shader1.getLog());
        if (!mapShader.isCompiled()) throw new GdxRuntimeException("MapShader hata: " + mapShader.getLog());

        TmxMapLoader loader = new TmxMapLoader();
        map = loader.load("flape.tmx");

        groundLayer      = (TiledMapTileLayer) map.getLayers().get(0);
        wallLayer        = (TiledMapTileLayer) map.getLayers().get("dk2");
        lowObstacleLayer = (TiledMapTileLayer) map.getLayers().get("dk3");
        renderer = new OrthogonalTiledMapRenderer(map, 3f);

        spawnManager.setGroundLayer(groundLayer);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        uiViewport.update(width, height, true);
        uiCamera.position.set(UI_WIDTH / 2, UI_HEIGHT / 2, 0);
        uiCamera.update();
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        if (shader1 != null) shader1.dispose();
        if (mapShader != null) mapShader.dispose();
        if (batch         != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (renderer != null) renderer.dispose();
        if (map      != null) map.dispose();
    }
}