import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class LoadingScreen implements Screen {
    private final Jgame game;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    private float animTimer = 0;

    public LoadingScreen(Jgame game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = game.font;
        Assets.load();
    }

    @Override
    public void render(float delta) {
        animTimer += delta;

        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float progress = Assets.getProgress();
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        float barWidth  = 400;
        float barHeight = 30;
        float barX      = (w - barWidth) / 2;
        float barY      = h / 2;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);

        float hue = (animTimer * 0.5f) % 1.0f;
        Color progressColor = new Color();
        progressColor.fromHsv(hue * 360, 0.7f, 0.9f);
        shapeRenderer.setColor(progressColor);
        shapeRenderer.rect(barX + 2, barY + 2, (barWidth - 4) * progress, barHeight - 4);
        shapeRenderer.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, game.bundle.format("loading.progress", (int)(progress * 100)),
                w / 2 - 100, barY + barHeight + 50);

        font.getData().setScale(0.6f);
        font.setColor(0.7f, 0.7f, 0.7f, 1);
        font.draw(batch, game.bundle.get("loading.message"), w / 2 - 250, barY - 30);
        font.getData().setScale(1f);
        batch.end();

        if (Assets.update()) {
            game.setScreen(new MainMenuScreen(game));
        }
    }

    @Override public void resize(int width, int height) {}
    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() { dispose(); }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}