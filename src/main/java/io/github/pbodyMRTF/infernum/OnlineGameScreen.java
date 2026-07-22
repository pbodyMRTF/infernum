package io.github.pbodyMRTF.infernum;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import shared.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OnlineGameScreen implements Screen {

    // --- Sabitler ---
    private static final float WORLD_WIDTH  = 1024f;
    private static final float WORLD_HEIGHT = 768f;
    private static final float UI_WIDTH     = 1024f;
    private static final float UI_HEIGHT    = 768f;

    // --- Core ---
    private final Jgame game;
    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;

    // --- Network ---
    private io.github.pbodyMRTF.infernum.NetworkClient networkClient;
    private int myPlayerId = -1;
    private boolean gameReady = false;
    private String serverHost;

    // --- Camera ---
    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;
    private ExtendViewport viewport;
    private ExtendViewport uiViewport;

    // --- Map ---
    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private TiledMapTileLayer groundLayer;

    // --- Shader ---
    private ShaderProgram shader1;
    private ShaderProgram mapShader;
    private float shaderTime = 0f;

    // --- Textures ---
    private Texture playerTex;
    private Texture enemyTex;
    private Texture enemy2Tex;
    private Texture enemy3Tex;
    private Texture bloodTex;
    private Texture heartTex;
    private Texture heartEmptyTex;
    private Texture bayonetTex;
    private Texture hotbar1, hotbar2, hotbar3;

    // --- Sounds ---
    private Sound shootSound, smgSound, shotgunSound, popSound, woodSound;

    // --- Input ---
    private static final int GAMEPAD_AXIS_RIGHT_X      = 2;
    private static final int GAMEPAD_AXIS_RIGHT_Y      = 3;
    private static final int GAMEPAD_AXIS_RIGHT_TRIGGER = 5;
    private static final int GAMEPAD_AXIS_LEFT_X       = 0;
    private static final int GAMEPAD_AXIS_LEFT_Y       = 1;
    private static final float DEADZONE         = 0.2f;
    private static final float TRIGGER_DEADZONE = 0.5f;
    private boolean prevTriggerFired = false;

    // --- Game state (server'dan gelir) ---
    private GameState currentState = null;

    // --- Görsel interpolasyon için son pozisyonlar ---
    private Map<Integer, float[]> entityPositions = new HashMap<>(); // id → [x, y]
    private Map<Integer, float[]> bulletPositions  = new HashMap<>();

    // --- Efektler (sadece görsel) ---
    private Array<BloodParticle> bloods = new Array<>();


    private static final float TICK_INTERVAL = 1f / 20f;

    private GameState prevState = null;
    private GameState targetState = null;
    private float interpTimer = 0f;

    private Texture bulletTex, bulletSmgTex, bulletPistolTex;

    public OnlineGameScreen(Jgame game, String serverHost) {
        this.game       = game;
        this.serverHost = serverHost;

        batch         = new SpriteBatch();
        font          = game.getFont(Jgame.FONT_SIZE_32);
        shapeRenderer = new ShapeRenderer();

        camera   = new OrthographicCamera();
        viewport = new ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);

        uiCamera   = new OrthographicCamera();
        uiViewport = new ExtendViewport(UI_WIDTH, UI_HEIGHT, uiCamera);
        uiCamera.position.set(UI_WIDTH / 2, UI_HEIGHT / 2, 0);
        uiCamera.update();

        loadAssets();
        connectToServer();
    }

    private void loadAssets() {
        playerTex     = Assets.getTexture(Assets.Textures.PLAYER);
        enemyTex      = Assets.getTexture(Assets.Textures.ENEMY);
        enemy2Tex     = Assets.getTexture(Assets.Textures.ENEMY2);
        enemy3Tex     = Assets.getTexture(Assets.Textures.ENEMY3);
        bloodTex      = Assets.getTexture(Assets.Textures.BLOOD);
        heartTex      = Assets.getTexture(Assets.Textures.HEART);
        heartEmptyTex = Assets.getTexture(Assets.Textures.HEART_EMPTY);
        bayonetTex    = Assets.getTexture(Assets.Textures.BAYONET);
        hotbar1       = Assets.getTexture(Assets.Textures.HOTBAR1);
        hotbar2       = Assets.getTexture(Assets.Textures.HOTBAR2);
        hotbar3       = Assets.getTexture(Assets.Textures.HOTBAR3);

        shootSound  = Assets.getSound(Assets.Sounds.SHOOT);
        smgSound    = Assets.getSound(Assets.Sounds.SMGSHOT);
        shotgunSound = Assets.getSound(Assets.Sounds.SHOTGUNSHOT);
        popSound    = Assets.getSound(Assets.Sounds.POP);
        woodSound   = Assets.getSound(Assets.Sounds.WOOD);

        bulletTex        = Assets.getTexture(Assets.Textures.BULLET);
        bulletSmgTex     = Assets.getTexture(Assets.Textures.BULLET_SMG);
        bulletPistolTex  = Assets.getTexture(Assets.Textures.BULLET_PISTOL);
    }

    private void connectToServer() {
        networkClient = new NetworkClient((pid) -> {
            // LibGDX ana thread'inde çalıştır
            Gdx.app.postRunnable(() -> {
                myPlayerId = pid;
                gameReady  = true;
                System.out.println("Oyun hazır! Ben oyuncu " + pid);
            });
        });

        new Thread(() -> {
            try {
                networkClient.connect(serverHost);
            } catch (IOException e) {
                Gdx.app.postRunnable(() ->
                        System.err.println("Bağlantı hatası: " + e.getMessage())
                );
            }
        }).start();
    }

    // ---------------------------------------------------------------
    // Render döngüsü
    // ---------------------------------------------------------------
    @Override
    public void render(float delta) {
        shaderTime += delta;
        interpTimer += delta;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        // Bekleme ekranı
        if (!gameReady) {
            renderWaiting();
            return;
        }

        // Input gönder
        sendInput();

        // Server state al
        GameState newState = networkClient.pollState();
        if (newState != null) {
            handleNewState(newState);
        }

        // Efektler güncelle
        for (BloodParticle b : bloods) b.update(delta);
        for (int i = bloods.size - 1; i >= 0; i--) {
            if (bloods.get(i).dead) bloods.removeIndex(i);
        }

        // Render
        if (groundLayer != null && currentState != null) {
            renderGame();
        }
    }

    // ---------------------------------------------------------------
    // Input toplama ve gönderme
    // ---------------------------------------------------------------
    private void sendInput() {
        PlayerInput input = new PlayerInput();
        input.playerId = myPlayerId;

        // Klavye hareket
        input.up    = Gdx.input.isKeyPressed(Input.Keys.W);
        input.down  = Gdx.input.isKeyPressed(Input.Keys.S);
        input.left  = Gdx.input.isKeyPressed(Input.Keys.A);
        input.right = Gdx.input.isKeyPressed(Input.Keys.D);

        // Silah seçimi
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) input.weaponSlot = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) input.weaponSlot = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) input.weaponSlot = 3;

        // Bayonet
        input.bayonetPressed = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);

        // Aim açısı (mouse)
        input.aimAngle = getAimAngle();

        // Ateş
        input.fireKeyboard = Gdx.input.isButtonPressed(Input.Buttons.LEFT);

        // Gamepad
        Controller c = Controllers.getControllers().size > 0
                ? Controllers.getControllers().first() : null;
        if (c != null) {
            float ax = c.getAxis(GAMEPAD_AXIS_LEFT_X);
            float ay = c.getAxis(GAMEPAD_AXIS_LEFT_Y);
            if (Math.abs(ax) > DEADZONE) input.gamepadMoveX = ax;
            if (Math.abs(ay) > DEADZONE) input.gamepadMoveY = ay;

            float rx = c.getAxis(GAMEPAD_AXIS_RIGHT_X);
            float ry = c.getAxis(GAMEPAD_AXIS_RIGHT_Y);
            if (Math.abs(rx) > DEADZONE || Math.abs(ry) > DEADZONE) {
                input.aimAngle = (float) Math.toDegrees(Math.atan2(-ry, rx));
            }

            boolean triggerNow = c.getAxis(GAMEPAD_AXIS_RIGHT_TRIGGER) > TRIGGER_DEADZONE;
            input.fireTrigger = triggerNow && !prevTriggerFired;
            prevTriggerFired  = triggerNow;
        }

        networkClient.sendInput(input);
    }

    private float getAimAngle() {
        // Kamera henüz yoksa 0 döndür
        if (currentState == null) return 0f;
        PlayerSnapshot me = getMySnapshot();
        if (me == null) return 0f;

        Vector3 mouse = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouse);
        return (float) Math.toDegrees(
                Math.atan2(mouse.y - (me.y + 32), mouse.x - (me.x + 32))
        );
    }

    // ---------------------------------------------------------------
    // State işleme
    // ---------------------------------------------------------------
    private void handleNewState(GameState state) {
        if (currentState != null) {
            for (PlayerSnapshot old : currentState.players) {
                for (PlayerSnapshot neu : state.players) {
                    if (old.playerId == neu.playerId && !old.dead && neu.dead) {
                        spawnBlood(neu.x + 32, neu.y + 32, 50);
                        woodSound.play(0.9f);
                    }
                }
            }
            for (EntitySnapshot old : currentState.entities) {
                for (EntitySnapshot neu : state.entities) {
                    if (old.id == neu.id && !old.dead && neu.dead) {
                        spawnBlood(neu.x + 32, neu.y + 32, 8);
                        popSound.play(0.7f);
                    }
                }
            }
        }

        // İnterpolasyon için: eski hedef → yeni "önceki", gelen → yeni "hedef"
        prevState   = (targetState != null) ? targetState : state;
        targetState = state;
        interpTimer = 0f;

        currentState = state; // score, game-over kontrolü gibi anlık şeyler için

        boolean allDead = state.players.stream().allMatch(p -> p.dead);
        if (allDead) {
            game.setScreen(new MainMenuScreen(game));
        }
        for (PlayerSnapshot ps : state.players) {
            if (ps.firedThisTick) {
                playShotSound(ps.firedBulletType);
            }
        }
    }
    private void playShotSound(byte bulletType) {
        // WeaponStats sabitleri: 0=AMMO(shotgun), 1=AMMO_SMG, 2=AMMO_PISTOL
        if (bulletType == 0) shotgunSound.play(0.7f);
        else if (bulletType == 1) smgSound.play(0.7f);
        else if (bulletType == 2) shootSound.play(0.7f);
    }

    // ---------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------
    private void renderWaiting() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.draw(batch, "Sunucuya bağlanılıyor...", 300, 400);
        if (networkClient.getMyPlayerId() >= 0 && !gameReady) {
            font.draw(batch, "Diğer oyuncu bekleniyor...", 300, 360);
        }
        batch.end();
    }
    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private PlayerSnapshot findPlayer(GameState s, int pid) {
        for (PlayerSnapshot p : s.players) if (p.playerId == pid) return p;
        return null;
    }

    private EntitySnapshot findEntity(GameState s, int id) {
        for (EntitySnapshot e : s.entities) if (e.id == id) return e;
        return null;
    }

    private BulletSnapshot findBullet(GameState s, int id) {
        for (BulletSnapshot b : s.bullets) if (b.id == id) return b;
        return null;
    }
    private Texture getBulletTexture(byte bulletType) {
        // WeaponStats: 0=AMMO(shotgun), 1=AMMO_SMG, 2=AMMO_PISTOL
        if (bulletType == 1) return bulletSmgTex;
        if (bulletType == 2) return bulletPistolTex;
        return bulletTex;
    }

    // x,y interpolasyonlu döndürür; prev'de yoksa (yeni spawn) direkt target kullanılır
    private float[] interpPos(float px, float py, float tx, float ty, boolean hasPrev, float t) {
        if (!hasPrev) return new float[]{tx, ty};
        return new float[]{lerp(px, py == py ? px : px, 0), 0}; // placeholder, aşağıda gerçek kullanım var
    }

    private void renderGame() {
        float t = MathUtils.clamp(interpTimer / TICK_INTERVAL, 0f, 1f);

        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        // --- Kamera: ölüysem diğer oyuncuyu izle ---
        PlayerSnapshot meTarget = findPlayer(targetState, myPlayerId);
        PlayerSnapshot camTarget = (meTarget != null && !meTarget.dead)
                ? meTarget
                : findOtherAlivePlayer(targetState, myPlayerId);

        if (camTarget != null) {
            PlayerSnapshot camPrev = findPlayer(prevState, camTarget.playerId);
            float mx = (camPrev != null) ? lerp(camPrev.x, camTarget.x, t) : camTarget.x;
            float my = (camPrev != null) ? lerp(camPrev.y, camTarget.y, t) : camTarget.y;
            camera.position.set(mx + 32, my + 32, 0);
            camera.position.x = MathUtils.clamp(camera.position.x, viewport.getWorldWidth()  / 2, mapWidth  - viewport.getWorldWidth()  / 2);
            camera.position.y = MathUtils.clamp(camera.position.y, viewport.getWorldHeight() / 2, mapHeight - viewport.getWorldHeight() / 2);
            camera.update();
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

        // --- Harita: mapShader ile ---
        mapRenderer.setView(camera);
        mapRenderer.getBatch().setShader(mapShader);
        if (mapShader.hasUniform("u_time"))
            mapShader.setUniformf("u_time", shaderTime);
        if (mapShader.hasUniform("u_resolution"))
            mapShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        mapRenderer.render();
        mapRenderer.getBatch().setShader(null);

        // --- Kan efektleri + mermiler + oyuncular (shader'sız) ---
        batch.setProjectionMatrix(camera.combined);
        batch.setShader(null);
        batch.begin();

        for (BloodParticle b : bloods) batch.draw(bloodTex, b.x, b.y);

        for (BulletSnapshot bt : targetState.bullets) {
            if (bt.dead) continue;
            BulletSnapshot bp = findBullet(prevState, bt.id);
            float bx = (bp != null) ? lerp(bp.x, bt.x, t) : bt.x;
            float by = (bp != null) ? lerp(bp.y, bt.y, t) : bt.y;
            batch.draw(getBulletTexture(bt.bulletType), bx, by);
        }

        for (PlayerSnapshot ps : targetState.players) {
            if (ps.dead) continue;
            PlayerSnapshot pp = findPlayer(prevState, ps.playerId);
            float px = (pp != null) ? lerp(pp.x, ps.x, t) : ps.x;
            float py = (pp != null) ? lerp(pp.y, ps.y, t) : ps.y;
            batch.draw(playerTex, px, py);
            drawGun(ps, px, py);
        }

        batch.end();

        // --- Düşmanlar: shader1 ile (u_health uniform) ---
        batch.setShader(shader1);
        batch.begin();
        if (shader1.hasUniform("u_time")) shader1.setUniformf("u_time", shaderTime);

        for (EntitySnapshot es : targetState.entities) {
            if (es.dead) continue;
            EntitySnapshot ep = findEntity(prevState, es.id);
            float ex = (ep != null) ? lerp(ep.x, es.x, t) : es.x;
            float ey = (ep != null) ? lerp(ep.y, es.y, t) : es.y;

            if (shader1.hasUniform("u_health")) {
                float healthRatio = es.maxHp > 0 ? (float) es.hp / es.maxHp : 1f;
                shader1.setUniformf("u_health", healthRatio);
            }
            batch.draw(getEntityTexture(es.type), ex, ey);
        }
        batch.end();
        batch.setShader(null);

        renderEnemyHealthBars();
        renderHUD();
    }

    private PlayerSnapshot findOtherAlivePlayer(GameState s, int excludePid) {
        for (PlayerSnapshot p : s.players) {
            if (p.playerId != excludePid && !p.dead) return p;
        }
        return null;
    }

    private void renderEnemyHealthBars() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (EntitySnapshot es : targetState.entities) {
            if (es.dead) continue;
            EntitySnapshot ep = findEntity(prevState, es.id);
            float t = MathUtils.clamp(interpTimer / TICK_INTERVAL, 0f, 1f);
            float ex = (ep != null) ? lerp(ep.x, es.x, t) : es.x;
            float ey = (ep != null) ? lerp(ep.y, es.y, t) : es.y;

            float bw = 64f, bh = 7f;
            float bx = ex, by = ey + 68f;

            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.85f);
            shapeRenderer.rect(bx, by, bw, bh);

            float ratio = es.maxHp > 0 ? MathUtils.clamp((float) es.hp / es.maxHp, 0f, 1f) : 1f;
            shapeRenderer.setColor(ratio > 0.5f ? 0.2f : 0.85f, ratio > 0.5f ? 0.85f : 0.3f, ratio > 0.5f ? 0.2f : 0.1f, 1f);
            shapeRenderer.rect(bx, by, bw * ratio, bh);
        }
        shapeRenderer.end();
    }

    private void drawGun(PlayerSnapshot p, float px, float py) {
        Weapons weapon = slotToWeapon(p.weaponSlot);
        if (weapon == null) return;
        Texture gunTex = Assets.getTexture(weapon.getGunTexturePath());
        float scale    = weapon.getGunScale();
        float angle    = p.aimAngle;

        batch.draw(
                gunTex,
                px + 32, py + 32,
                0, gunTex.getHeight() / 2f,
                gunTex.getWidth(), gunTex.getHeight(),
                scale, scale,
                angle,
                0, 0, gunTex.getWidth(), gunTex.getHeight(),
                false, false
        );
    }

    private void renderHUD() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        // Score
        font.draw(batch, "Score: " + currentState.score, 20, UI_HEIGHT - 20);

        // Her oyuncunun HP'si
        for (PlayerSnapshot p : currentState.players) {
            float yOffset = p.playerId == 0 ? UI_HEIGHT - 60 : UI_HEIGHT - 120;
            font.draw(batch, "P" + (p.playerId + 1) + ": ", 20, yOffset);
            for (int i = 0; i < 3; i++) {
                Texture h = i < p.hp ? heartTex : heartEmptyTex;
                batch.draw(h, 80 + i * 40, yOffset - 32, 32, 32);
            }
        }

        // Hotbar (benim silahım) — orijinaliyle birebir aynı
        PlayerSnapshot me = getMySnapshot();
        float hotbarWidth  = 100;
        float hotbarHeight = 260;
        float hotbarX      = UI_WIDTH + (hotbarWidth / 2);
        float hotbarY      = (UI_HEIGHT / 2) - (hotbarHeight / 2);

        Texture current = hotbar1;
        if (me != null) {
            current = switch (me.weaponSlot) {
                case 1 -> hotbar1;
                case 2 -> hotbar2;
                case 3 -> hotbar3;
                default -> current;
            };
        }
        batch.draw(current, hotbarX, hotbarY, hotbarWidth, hotbarHeight);

        batch.end();
    }

    // ---------------------------------------------------------------
    // Yardımcı
    // ---------------------------------------------------------------
    private void spawnBlood(float x, float y, int count) {
        for (int i = 0; i < count; i++) bloods.add(new BloodParticle(x, y, bloodTex));
    }

    private PlayerSnapshot getMySnapshot() {
        if (targetState == null) return null;
        for (PlayerSnapshot p : targetState.players)
            if (p.playerId == myPlayerId) return p;
        return null;
    }

    private Texture getEntityTexture(byte type) {
        if (type == EntitySnapshot.TYPE_ENEMY2) return enemy2Tex;
        if (type == EntitySnapshot.TYPE_ENEMY3) return enemy3Tex;
        return enemyTex;
    }

    private Weapons slotToWeapon(int slot) {
        if (slot == 1) return new Weapons(Weapons.WeaponType.PISTOL);
        if (slot == 2) return new Weapons(Weapons.WeaponType.SHOTGUN);
        if (slot == 3) return new Weapons(Weapons.WeaponType.SMG);
        return null;
    }

    // ---------------------------------------------------------------
    // Screen lifecycle
    // ---------------------------------------------------------------
    @Override
    public void show() {
        ShaderProgram.pedantic = false;
        shader1 = new ShaderProgram(
                Gdx.files.internal("shaders/red.vsh"),
                Gdx.files.internal("shaders/red.fsh"));
        mapShader = new ShaderProgram(
                Gdx.files.internal("shaders/map.vsh"),
                Gdx.files.internal("shaders/map.fsh"));

        if (!shader1.isCompiled())   throw new GdxRuntimeException("shader1: " + shader1.getLog());
        if (!mapShader.isCompiled()) throw new GdxRuntimeException("mapShader: " + mapShader.getLog());

        map         = new TmxMapLoader().load("flape.tmx");
        groundLayer = (TiledMapTileLayer) map.getLayers().get(0);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 3f);
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
        if (networkClient != null) networkClient.disconnect();
        if (shader1    != null) shader1.dispose();
        if (mapShader  != null) mapShader.dispose();
        if (batch      != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (mapRenderer   != null) mapRenderer.dispose();
        if (map           != null) map.dispose();
    }
}