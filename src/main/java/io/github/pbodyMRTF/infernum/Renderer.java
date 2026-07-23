package io.github.pbodyMRTF.infernum;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class Renderer {
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private OrthogonalTiledMapRenderer mapRenderer;
    private ShaderProgram shader1;
    private ShaderProgram mapShader;
    private ShaderProgram whiteShader;
    private TiledMapTileLayer groundLayer;
    private EntityManager entityManager;
    private Texture bloodTex;
    private Texture tozTex;
    private Texture bayonetTex;
    private HUD hud;
    private DamageFlashManager damageFlashManager;
    private LightingManager lighting;

    public Renderer(SpriteBatch batch, OrthographicCamera camera, OrthographicCamera uiCamera,
                    ExtendViewport viewport, OrthogonalTiledMapRenderer mapRenderer,
                    ShaderProgram shader1, ShaderProgram mapShader, ShaderProgram whiteShader,
                    TiledMapTileLayer groundLayer, EntityManager entityManager,
                    Texture bloodTex, Texture tozTex, Texture bayonetTex, HUD hud,
                    DamageFlashManager damageFlashManager, LightingManager lighting) {
        this.batch         = batch;
        this.camera        = camera;
        this.viewport      = viewport;
        this.mapRenderer   = mapRenderer;
        this.shader1       = shader1;
        this.mapShader     = mapShader;
        this.whiteShader   = whiteShader;
        this.groundLayer   = groundLayer;
        this.entityManager = entityManager;
        this.bloodTex      = bloodTex;
        this.tozTex        = tozTex;
        this.bayonetTex    = bayonetTex;
        this.hud           = hud;
        this.damageFlashManager = damageFlashManager;
        this.lighting      = lighting;
    }

    public void render(float shaderTime, Player player,
                       Array<Bullet> bullets, Array<BloodParticle> bloods, Array<toz> tozlar,
                       boolean showBayonetAnim, float bayonetAnimTime,
                       boolean isSlowed, float slowRemaining, int score,
                       GameTickManager.TickTimer bayonetCooldown, int currentTick) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);


        float maxLookAheadDistance = 60f;


        float targetX = player.getCenterX();
        float targetY = player.getCenterY();



        Vector3 mouseInWorld = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseInWorld);


        float distX = mouseInWorld.x - player.getCenterX();
        float distY = mouseInWorld.y - player.getCenterY();
        float distance = Vector2.len(distX, distY);

        // nomalize
        if (distance > 1f) {
            float lookFactor = Math.min(distance / 300f, 1f);
            targetX += (distX / distance) * (maxLookAheadDistance * lookFactor);
            targetY += (distY / distance) * (maxLookAheadDistance * lookFactor);
        }



        // Gdx.graphics.getDeltaTime() * 10f buradaki geçiş hızını belirler (değeri büyüttükçe hızlanır)
        camera.position.x = MathUtils.lerp(camera.position.x, targetX, Gdx.graphics.getDeltaTime() * 5f);
        camera.position.y = MathUtils.lerp(camera.position.y, targetY, Gdx.graphics.getDeltaTime() * 5f);

        // 5. Harita sınırları dışına çıkmasını engelleme (Clamp)
        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        camera.position.x = MathUtils.clamp(camera.position.x, viewport.getWorldWidth()  / 2, mapWidth  - viewport.getWorldWidth()  / 2);
        camera.position.y = MathUtils.clamp(camera.position.y, viewport.getWorldHeight() / 2, mapHeight - viewport.getWorldHeight() / 2);

        camera.update();

        renderMap(shaderTime);
        renderBayonetAnim(player, showBayonetAnim, bayonetAnimTime);
        renderWorld(player, bullets, bloods, tozlar, currentTick);
        renderEnemies(shaderTime, currentTick);
        lighting.render(camera);
        hud.render(batch, player, isSlowed, slowRemaining, score, bayonetCooldown, currentTick);
    }

    private void renderMap(float shaderTime) {
        mapRenderer.setView(camera);
        mapRenderer.getBatch().setShader(mapShader);
        if (mapShader.hasUniform("u_time"))       mapShader.setUniformf("u_time", shaderTime);
        if (mapShader.hasUniform("u_resolution")) mapShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        mapRenderer.render();
        mapRenderer.getBatch().setShader(null);
    }

    private void renderBayonetAnim(Player player, boolean showBayonetAnim, float bayonetAnimTime) {
        if (!showBayonetAnim) return;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float bicakBoyutuX    = 16f;
        float bicakBoyutuY    = 32f;
        float yorungeUzakligi = 60f;
        float rotation        = (bayonetAnimTime / 0.3f) * 360f * 1.5f;
        float alpha           = 1f - (bayonetAnimTime / 0.3f);
        float drawX           = player.getCenterX() + MathUtils.cosDeg(rotation) * yorungeUzakligi;
        float drawY           = player.getCenterY() + MathUtils.sinDeg(rotation) * yorungeUzakligi;
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(bayonetTex,
                drawX - (bicakBoyutuX / 2), drawY - (bicakBoyutuY / 2),
                bicakBoyutuX / 2, bicakBoyutuY / 2,
                bicakBoyutuX, bicakBoyutuY,
                1f, 1f, rotation + (-90f),
                0, 0, bayonetTex.getWidth(), bayonetTex.getHeight(),
                false, false);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void renderWorld(Player player, Array<Bullet> bullets, Array<BloodParticle> bloods, Array<toz> tozlar, int currentTick) {
        batch.setProjectionMatrix(camera.combined);

        // Player kendisi: hasar aldıysa whiteShader ile, aksi halde normal (shader yok)
        boolean playerFlashing = damageFlashManager.isPlayerFlashing(currentTick);
        batch.setShader(playerFlashing ? whiteShader : null);
        batch.begin();
        player.draw(batch);
        batch.end();

        // Geri kalan her şey (silah, mermiler, kan, toz) her zaman normal render edilir
        batch.setShader(null);
        batch.begin();
        player.drawGun(batch, camera);
        for (Bullet b : bullets)       batch.draw(b.getTexture(), b.x, b.y);
        for (BloodParticle b : bloods) batch.draw(bloodTex, b.x, b.y);
        for (toz t : tozlar)           batch.draw(tozTex, t.x, t.y);
        batch.end();
    }

    private void renderEnemies(float shaderTime, int currentTick) {
        // 1. geçiş: hasar flaşı YAŞAMAYAN düşmanlar -> normal shader1 (kırmızı hasar shader'ı)
        batch.setShader(shader1);
        batch.begin();
        if (shader1.hasUniform("u_time")) shader1.setUniformf("u_time", shaderTime);
        for (Entity e : entityManager.getAll()) {
            if (e.isDead()) continue;
            if (damageFlashManager.isEnemyFlashing(e, currentTick)) continue;
            if (shader1.hasUniform("u_health")) {
                shader1.setUniformf("u_health", e.getHp() / e.getMaxHp());
            }
            batch.draw(e.getTexture(), e.getX(), e.getY());
        }
        batch.end();

        // 2. geçiş: hasar flaşı YAŞAYAN düşmanlar -> whiteShader
        batch.setShader(whiteShader);
        batch.begin();
        for (Entity e : entityManager.getAll()) {
            if (e.isDead()) continue;
            if (!damageFlashManager.isEnemyFlashing(e, currentTick)) continue;
            batch.draw(e.getTexture(), e.getX(), e.getY());
        }
        batch.end();

        batch.setShader(null);
    }
}