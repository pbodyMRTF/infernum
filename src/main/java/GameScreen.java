import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
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
import com.badlogic.gdx.utils.I18NBundle;
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
    private Texture bulletTex;
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
    private Array<Enemy> enemies = new Array<>();
    private Array<Enemy2> enemies2 = new Array<>();
    private Array<Enemy3> enemies3 = new Array<>();
    private Array<BloodParticle> bloods = new Array<>();
    private Array<toz> tozlar = new Array<>();

    private GameTickManager.TickTimer shootCooldown;
    private GameTickManager.TickTimer hitCooldown;
    private GameTickManager.TickTimer slowdownTimer;
    private GameTickManager.TickTimer deathTimer;
    private GameTickManager.TickTimer bayonetCooldown;
    private int lastSpawnTick = 0;

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

        shootCooldown  = new GameTickManager.TickTimer(shootCooldownTicks);
        hitCooldown    = new GameTickManager.TickTimer(hitCooldownTicks);
        slowdownTimer  = new GameTickManager.TickTimer(slowdownTicks);
        deathTimer     = new GameTickManager.TickTimer(deathDelayTicks);
        bayonetCooldown = new GameTickManager.TickTimer(bayonetCooldownTicks);

        loadAssets();

        spawnPlayer();
    }

    private void applyDifficultySettings() {
        if (difficulty.equals("ZOR")) {
            baseSpawnInterval  = 0.6f;
            minSpawnInterval   = 0.1f;
            shootCooldownTicks = 16;
            hitCooldownTicks   = 10;
        } else if (difficulty.equals("Ben Erlik Han'ım")) {
            baseSpawnInterval  = 0.01f;
            minSpawnInterval   = 0f;
            shootCooldownTicks = 20;
            hitCooldownTicks   = 5;
        } else {
            baseSpawnInterval  = 1.5f;
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

        bulletTex    = Assets.getTexture(Assets.Textures.BULLET);
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
                return useBayonet();
            }
        });
    }

    private int useBayonet() {
        int killedCount = 0;
        sliceSound.play(1.2f);

        showBayonetAnim = true;
        bayonetAnimTime = 0f;
        bayonetCooldown.start(tickManager.getCurrentTick());

        for (Enemy e : enemies) {
            if (!e.dead && isInBayonetRange(e.x, e.y)) {
                e.dead = true;
                createBloodEffect(e.x, e.y);
                popSound.play(1f);
                score++;
                killedCount++;
            }
        }

        for (Enemy2 e : enemies2) {
            if (!e.dead && isInBayonetRange(e.x, e.y)) {
                e.dead = true;
                createBloodEffect(e.x, e.y);
                popSound.play(1f);
                score++;
                killedCount++;
            }
        }

        for (Enemy3 e : enemies3) {
            if (!e.dead && isInBayonetRange(e.x, e.y)) {
                e.dead = true;
                createBloodEffect(e.x, e.y);
                popSound.play(1f);
                score++;
                killedCount++;
            }
        }

        return killedCount;
    }

    private boolean isInBayonetRange(float ex, float ey) {
        float dist = Vector2.dst(ex + 32, ey + 32, player.getCenterX(), player.getCenterY());
        return dist < BAYONET_RANGE;
    }

    private void handleTick(int currentTick) {
        fpsTickCounter++;
        if (fpsTickCounter >= 20) fpsTickCounter = 0;

        handleEnemySpawn(currentTick);
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

    private void spawnEnemy() {
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        float spawnX, spawnY;
        int side = game.rnd.nextInt(4);

        switch (side) {
            case 0:  spawnX = -50;           spawnY = game.rnd.nextFloat() * mapHeight; break;
            case 1:  spawnX = mapWidth + 50; spawnY = game.rnd.nextFloat() * mapHeight; break;
            case 2:  spawnX = game.rnd.nextFloat() * mapWidth; spawnY = mapHeight + 50; break;
            default: spawnX = game.rnd.nextFloat() * mapWidth; spawnY = -50;            break;
        }

        int enemyType = game.rnd.nextInt(10);
        if (enemyType < 4)      enemies.add(new Enemy(spawnX, spawnY)); // %40 4/10
        else if (enemyType < 8) enemies2.add(new Enemy2(spawnX, spawnY));// %40 4/10
        else                    enemies3.add(new Enemy3(spawnX, spawnY));// %20 2/10
    }

    private void handleEnemySpawn(int currentTick) {
        float currentSpawnInterval = Math.max(minSpawnInterval, baseSpawnInterval - (score * 0.01f));
        int spawnIntervalTicks = (int)(currentSpawnInterval * 20);

        if (tickManager.hasTicksPassed(lastSpawnTick, spawnIntervalTicks)) {
            spawnEnemy();
            lastSpawnTick = currentTick;
        }
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
        handleShooting();
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

    private void handleShooting() {
        if (player.dead) return;
        Weapons w = player.getWeapon();
        if (w == null) return;

        boolean triggerNow  = player.isTriggerPressed();

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
                case PISTOL:
                default:
                    shootSound.play(0.7f);
                    bulletType = Bullet.BulletType.AMMO_PISTOL;
                    break;
            }

            for (int i = 0; i < w.getBulletCount(); i++) {
                float spread = MathUtils.random(-w.getBulletSpread(), w.getBulletSpread());
                bullets.add(new Bullet(
                        player.getCenterX(),
                        player.getCenterY(),
                        baseAngle + spread,
                        bulletType
                ));
            }

            shootCooldown = new GameTickManager.TickTimer(w.getFireRateTicks());
            shootCooldown.start(tickManager.getCurrentTick());
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
        for (Enemy  e : enemies)  e.update(delta, player.x, player.y);
        for (Enemy2 e : enemies2) e.update(delta, player.x, player.y);
        for (Enemy3 e : enemies3) e.update(delta, player.x, player.y);
    }

    private void handleCollisions() {
        handleBulletEnemyCollision();
        handlePlayerEnemyCollision();
        handlePlayerBloodCollision();
    }

    private void handleBulletEnemyCollision() {
        for (Enemy e : enemies) {
            for (Bullet b : bullets) {
                if (!e.dead && checkBulletCollision(e.x, e.y, b.x, b.y)) {
                    int damage = 1;
                    switch (b.getBulletType()) {
                        case AMMO_SMG:    splatSound.play(); damage = 15; break;
                        case AMMO_PISTOL: tinSound.play(1f); damage = 3;  break;
                        case AMMO:        tinSound.play(1f); damage = 2;  break;
                    }
                    e.hp -= damage;
                    if (e.hp <= 0) { createBloodEffect(e.x, e.y); popSound.play(0.7f); score++; e.dead = true; }
                    b.dead = true;
                }
            }
        }

        for (Enemy2 e : enemies2) {
            for (Bullet b : bullets) {
                if (!e.dead && checkBulletCollision(e.x, e.y, b.x, b.y)) {
                    int damage = 1;
                    switch (b.getBulletType()) {
                        case AMMO:        splatSound.play(); damage = 30; break;
                        case AMMO_SMG:    tinSound.play(1f); damage = 5;  popSound.play(0.2f); break;
                        case AMMO_PISTOL: tinSound.play(1f); damage = 14;  popSound.play(0.2f); break;
                    }
                    e.hp -= damage;
                    if (e.hp <= 0) { createTozEffect(e.x, e.y); createBloodEffect(e.x, e.y); popSound.play(0.7f); score++; e.dead = true; }
                    b.dead = true;
                }
            }
        }

        for (Enemy3 e : enemies3) {
            for (Bullet b : bullets) {
                if (!e.dead && checkBulletCollision(e.x, e.y, b.x, b.y)) {
                    float damage = 1;
                    switch (b.getBulletType()) {
                        case AMMO_PISTOL: splatSound.play(); damage = 8;   break;
                        case AMMO_SMG:    tinSound.play(1f); damage = 2; break;
                        case AMMO:        tinSound.play(1f); damage = 1; break;
                    }
                    e.hp -= damage;
                    if (e.hp <= 0) { createBloodEffect(e.x, e.y); popSound.play(0.7f); score++; e.dead = true; }
                    b.dead = true;
                }
            }
        }
    }

    private boolean checkBulletCollision(float ex, float ey, float bx, float by) {
        float enemyRadius  = 32;
        float bulletRadius = 4;
        float dist = Vector2.dst(ex + enemyRadius, ey + enemyRadius, bx + bulletRadius, by + bulletRadius);
        return dist < enemyRadius + bulletRadius;
    }

    private void createBloodEffect(float x, float y) {
        for (int i = 0; i < 8; i++) bloods.add(new BloodParticle(x + 32, y + 32, bloodTex));
    }

    private void createTozEffect(float x, float y) {
        for (int i = 0; i < 8; i++) tozlar.add(new toz(x + 32, y + 32, tozTex));
    }

    private void handlePlayerEnemyCollision() {
        if (player.dead || hitCooldown.isRunning()) return;

        for (Enemy  e : enemies)  { if (checkPlayerCollision(e.x, e.y)) { playerTakeDamage(); return; } }
        for (Enemy2 e : enemies2) { if (checkPlayerCollision(e.x, e.y)) { playerTakeDamage(); return; } }
        for (Enemy3 e : enemies3) { if (checkPlayerCollision(e.x, e.y)) { playerTakeDamage(); return; } }
    }

    private boolean checkPlayerCollision(float ex, float ey) {
        float r    = 32;
        float dist = Vector2.dst(ex + r, ey + r, player.getCenterX(), player.getCenterY());
        return dist < r * 2;
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

    private void handlePlayerBloodCollision() {
        if (player.dead || hitCooldown.isRunning()) return;

        for (BloodParticle b : bloods) {
            if (b.dead) continue;
            float bloodRadius  = 4;
            float playerRadius = 32;
            float dist = Vector2.dst(b.x + bloodRadius, b.y + bloodRadius, player.getCenterX(), player.getCenterY());
            if (dist < bloodRadius + playerRadius) {
                isSlowed = true;
                slowdownTimer.start(tickManager.getCurrentTick());
                player.slowDown(300);
                b.dead = true;
                popSound.play(0.3f);
            }
        }
    }

    private void cleanupDeadObjects() {
        for (int i = enemies.size  - 1; i >= 0; i--) if (enemies.get(i).dead)  enemies.removeIndex(i);
        for (int i = enemies2.size - 1; i >= 0; i--) if (enemies2.get(i).dead) enemies2.removeIndex(i);
        for (int i = enemies3.size - 1; i >= 0; i--) if (enemies3.get(i).dead) enemies3.removeIndex(i);
        for (int i = bullets.size  - 1; i >= 0; i--) if (bullets.get(i).dead)  bullets.removeIndex(i);
        for (int i = bloods.size   - 1; i >= 0; i--) if (bloods.get(i).dead)   bloods.removeIndex(i);
        for (int i = tozlar.size   - 1; i >= 0; i--) if (tozlar.get(i).dead)   tozlar.removeIndex(i);
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
        for (Enemy  e : enemies)  batch.draw(enemyTex,  e.x, e.y);
        for (Enemy2 e : enemies2) batch.draw(enemy2Tex, e.x, e.y);
        for (Enemy3 e : enemies3) batch.draw(enemy3Tex, e.x, e.y);
        batch.end();

        batch.setShader(null);
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        renderUI();
        drawHealthBars();
        renderHotbar();
        renderHearts();
        renderBayonetCooldownBar();
        batch.end();
    }

    private void renderUI() {
        font.setColor(Color.WHITE);

        if (isSlowed) {
            font.getData().setScale(0.8f);
            float remaining = slowdownTimer.getRemainingSeconds(tickManager.getCurrentTick());
            font.draw(batch, game.bundle.format("game.ui.slowed", remaining), UI_WIDTH / 2, UI_HEIGHT - 20);
            font.getData().setScale(1f);
        }
        font.getData().setScale(0.9f);
        font.draw(batch, game.bundle.format("game.ui.score", score), UI_WIDTH / 2, 38);
        font.getData().setScale(1f);
    }

    private void renderHotbar() {
        float hotbarWidth  = 100;
        float hotbarHeight = 260;
        float hotbarX      = (UI_WIDTH) + (hotbarWidth / 2);
        float hotbarY      = (UI_HEIGHT / 2) - (hotbarHeight / 2);

        Texture currentHotbar = Hotbar1;

        if (player != null && player.getWeapon() != null) {
            switch (player.getWeapon().getType()) {
                case PISTOL:  currentHotbar = Hotbar1; break;
                case SHOTGUN: currentHotbar = Hotbar2; break;
                case SMG:     currentHotbar = Hotbar3; break;
            }
        }

        batch.draw(currentHotbar, hotbarX, hotbarY, hotbarWidth, hotbarHeight);
    }

    private void renderHearts() {
        int   maxHp    = 3;
        float heartSize = 64;
        float startX   = 20;
        float startY   = UI_HEIGHT - 58;

        for (int i = 0; i < maxHp; i++) {
            Texture heart;
            if (i < player.getHp()) {
                heart = player.isRegenHeart(i) ? regenHeartTex : heartTex;
            } else {
                heart = heartEmptyTex;
            }
            batch.draw(heart, startX + i * (heartSize + 5), startY, heartSize, heartSize);
        }
    }

    private void drawHealthBars() {
        batch.end();

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Enemy  e : enemies)  drawBar(e.x, e.y, e.hp, e.maxHp);
        for (Enemy2 e : enemies2) drawBar(e.x, e.y, e.hp, e.maxHp);
        for (Enemy3 e : enemies3) drawBar(e.x, e.y, e.hp, e.maxHp);
        shapeRenderer.end();

        batch.begin();
    }

    private void drawBar(float x, float y, int hp, int maxHp) {
        if (hp <= 0) return;
        float bw = 64f, bh = 7f;
        float bx = x, by = y + 68f;

        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.85f);
        shapeRenderer.rect(bx, by, bw, bh);

        float ratio = MathUtils.clamp((float) hp / maxHp, 0f, 1f);
        if (ratio > 0.5f) shapeRenderer.setColor(0.2f, 0.85f, 0.2f, 1f);
        else               shapeRenderer.setColor(0.85f, 0.3f, 0.1f, 1f);
        shapeRenderer.rect(bx, by, bw * ratio, bh);
    }

    private void renderBayonetCooldownBar() {
        batch.end();

        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float barWidth  = 200;
        float barHeight = 20;
        float barX      = UI_WIDTH - barWidth - 20;
        float barY      = 40;

        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);

        if (bayonetCooldown != null) {
            float progress = bayonetCooldown.isRunning()
                    ? bayonetCooldown.getProgress(tickManager.getCurrentTick())
                    : 1.0f;

            if (progress >= 1.0f) {
                shapeRenderer.setColor(0.2f, 0.8f, 0.2f, 1.0f);
            } else {
                float r = 1.0f - (progress * 0.5f);
                float g = progress * 0.8f;
                shapeRenderer.setColor(r, g, 0.1f, 1.0f);
            }
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight);
        }

        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 1f);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);
        shapeRenderer.end();

        batch.begin();

        font.setColor(Color.WHITE);
        font.getData().setScale(0.5f);
        String label = game.bundle.get("game.ui.bayonet");
        if (bayonetCooldown != null && bayonetCooldown.isRunning()) {
            label += game.bundle.format("game.ui.bayonet.cooldown",
                    bayonetCooldown.getRemainingSeconds(tickManager.getCurrentTick()));
        } else {
            label += game.bundle.get("game.ui.bayonet.ready");
        }
        font.draw(batch, label, barX, barY + barHeight + 18);
        font.getData().setScale(1f);
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