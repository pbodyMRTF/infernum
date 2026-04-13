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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class Renderer {
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private OrthogonalTiledMapRenderer mapRenderer;
    private ShaderProgram shader1;
    private ShaderProgram mapShader;
    private TiledMapTileLayer groundLayer;
    private EntityManager entityManager;
    private Texture bloodTex;
    private Texture tozTex;
    private Texture bayonetTex;
    private HUD hud;

    public Renderer(SpriteBatch batch, OrthographicCamera camera, OrthographicCamera uiCamera,
                    ExtendViewport viewport, OrthogonalTiledMapRenderer mapRenderer,
                    ShaderProgram shader1, ShaderProgram mapShader,
                    TiledMapTileLayer groundLayer, EntityManager entityManager,
                    Texture bloodTex, Texture tozTex, Texture bayonetTex, HUD hud) {
        this.batch         = batch;
        this.camera        = camera;
        this.viewport      = viewport;
        this.mapRenderer   = mapRenderer;
        this.shader1       = shader1;
        this.mapShader     = mapShader;
        this.groundLayer   = groundLayer;
        this.entityManager = entityManager;
        this.bloodTex      = bloodTex;
        this.tozTex        = tozTex;
        this.bayonetTex    = bayonetTex;
        this.hud           = hud;
    }

    public void render(float shaderTime, Player player,
                       Array<Bullet> bullets, Array<BloodParticle> bloods, Array<toz> tozlar,
                       boolean showBayonetAnim, float bayonetAnimTime,
                       boolean isSlowed, float slowRemaining, int score,
                       GameTickManager.TickTimer bayonetCooldown, int currentTick) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float mapWidth  = groundLayer.getWidth()  * groundLayer.getTileWidth()  * 3f;
        float mapHeight = groundLayer.getHeight() * groundLayer.getTileHeight() * 3f;

        camera.position.set(player.getCenterX(), player.getCenterY(), 0);
        camera.position.x = MathUtils.clamp(camera.position.x, viewport.getWorldWidth()  / 2, mapWidth  - viewport.getWorldWidth()  / 2);
        camera.position.y = MathUtils.clamp(camera.position.y, viewport.getWorldHeight() / 2, mapHeight - viewport.getWorldHeight() / 2);
        camera.update();

        renderMap(shaderTime);
        renderBayonetAnim(player, showBayonetAnim, bayonetAnimTime);
        renderWorld(player, bullets, bloods, tozlar);
        renderEnemies(shaderTime);
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

    private void renderWorld(Player player, Array<Bullet> bullets, Array<BloodParticle> bloods, Array<toz> tozlar) {
        batch.setProjectionMatrix(camera.combined);
        batch.setShader(null);
        batch.begin();
        player.draw(batch);
        player.drawGun(batch, camera);
        for (Bullet b : bullets)       batch.draw(b.getTexture(), b.x, b.y);
        for (BloodParticle b : bloods) batch.draw(bloodTex, b.x, b.y);
        for (toz t : tozlar)           batch.draw(tozTex, t.x, t.y);
        batch.end();
    }

    private void renderEnemies(float shaderTime) {
        batch.setShader(shader1);
        batch.begin();
        if (shader1.hasUniform("u_time")) shader1.setUniformf("u_time", shaderTime);
        for (Entity e : entityManager.getAll()) {
            if (e.isDead()) continue;  // ölüyse atla
            if (shader1.hasUniform("u_health")) {
                shader1.setUniformf("u_health", e.getHp() / e.getMaxHp());
            }
            batch.draw(e.getTexture(), e.getX(), e.getY());
        }
        batch.end();
        batch.setShader(null);
    }
}