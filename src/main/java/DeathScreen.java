import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.util.Random;
public class DeathScreen implements Screen {

    // ── Sabitler ────────────────────────────────────────────────────────────
    private static final float UI_WIDTH  = 1024f;
    private static final float UI_HEIGHT = 768f;

    private static final int GAMEPAD_BUTTON_A    = 0;
    private static final int GAMEPAD_AXIS_LEFT_Y = 1;
    private static final float DEADZONE          = 0.5f;

    private static final int   PARTICLE_COUNT  = 60;
    private static final int   OPTION_COUNT    = 2;   // 0 = Tekrar Oyna, 1 = Ana Menü

    // ── Bağımlılıklar ────────────────────────────────────────────────────────
    private final Jgame game;
    private final int   finalScore;

    // ── Render araçları ──────────────────────────────────────────────────────
    private SpriteBatch   batch;
    private ShapeRenderer shapeRenderer;

    private BitmapFont fontTitle;
    private BitmapFont fontScore;
    private BitmapFont fontMenu;
    private BitmapFont fontHint;

    private final GlyphLayout layout = new GlyphLayout();

    // ── Kamera / Viewport ───────────────────────────────────────────────────
    private OrthographicCamera camera;
    private ExtendViewport      viewport;

    // ── Animasyon durumu ─────────────────────────────────────────────────────
    private float fadeAlpha      = 0f;   // genel fade-in (0 → 1)
    private float titlePulse     = 0f;   // başlık salınım zamanı
    private float titleScale     = 1f;   // hesaplanan anlık ölçek
    private float selectionBlink = 0.75f;
    private float bgRedIntensity = 0f;   // arkaplan kırmızı yoğunluğu (0 → 0.18)

    // ── Menü durumu ──────────────────────────────────────────────────────────
    private int selectedOption = 0;

    // ── Gamepad önceki-kare durumu ───────────────────────────────────────────
    private boolean prevButtonA   = false;
    private boolean prevStickUp   = false;
    private boolean prevStickDown = false;

    // ── Parçacıklar ──────────────────────────────────────────────────────────
    private final float[] particleX     = new float[PARTICLE_COUNT];
    private final float[] particleY     = new float[PARTICLE_COUNT];
    private final float[] particleSpeed = new float[PARTICLE_COUNT];
    private final float[] particleSize  = new float[PARTICLE_COUNT];
    private final float[] particleAlpha = new float[PARTICLE_COUNT];

    // ────────────────────────────────────────────────────────────────────────
    public DeathScreen(final Jgame game, int finalScore) {
        this.game       = game;
        this.finalScore = finalScore;

        batch         = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

        fontTitle = game.getFont(Jgame.FONT_SIZE_256);
        fontScore = game.getFont(Jgame.FONT_SIZE_64);
        fontMenu  = game.getFont(Jgame.FONT_SIZE_32);
        fontHint  = game.getFont(Jgame.FONT_SIZE_16);

        camera   = new OrthographicCamera();
        viewport = new ExtendViewport(UI_WIDTH, UI_HEIGHT, camera);
        camera.position.set(UI_WIDTH / 2f, UI_HEIGHT / 2f, 0);
        camera.update();

        initParticles();
    }

    // ── Parçacık başlatma ────────────────────────────────────────────────────
    private void initParticles() {
        Random rand = new Random();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i]     = rand.nextFloat() * UI_WIDTH;
            particleY[i]     = rand.nextFloat() * UI_HEIGHT;
            particleSpeed[i] = 25f + rand.nextFloat() * 55f;
            particleSize[i]  = 1.5f + rand.nextFloat() * 3.5f;
            particleAlpha[i] = 0.15f + rand.nextFloat() * 0.35f;
        }
    }

    // ── Ana render döngüsü ────────────────────────────────────────────────────
    @Override
    public void render(float delta) {
        updateAnimations(delta);

        // Arkaplan: siyahdan koyu kırmızıya geçiş
        float r = bgRedIntensity * fadeAlpha;
        Gdx.gl.glClearColor(r, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();

        // --- Parçacıklar (ShapeRenderer, projection ayrı) ---
        shapeRenderer.setProjectionMatrix(camera.combined);
        drawParticles(delta);

        // --- Metin katmanı ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        float cx = UI_WIDTH  / 2f;
        float cy = UI_HEIGHT / 2f;

        // "YOU DIED"
        float scale = 1f + titleScale * 0.045f;
        fontTitle.getData().setScale(scale);
        fontTitle.setColor(1f, 0.08f + titleScale * 0.06f, 0.08f, fadeAlpha);
        String titleText = game.bundle.get("death.title");   // "YOU DIED"
        layout.setText(fontTitle, titleText);
        fontTitle.draw(batch, titleText,
                cx - layout.width / 2f,
                cy + 200f);
        fontTitle.getData().setScale(1f);
        fontTitle.setColor(1f, 1f, 1f, 1f);

        // Skor
        fontScore.setColor(0.9f, 0.9f, 0.9f, fadeAlpha * 0.85f);
        String scoreText = game.bundle.format("death.score");
        layout.setText(fontScore, scoreText);
        fontScore.draw(batch, scoreText + +  GameScreen.getScore(), cx - layout.width / 2f, cy + 30f);
        fontScore.setColor(1f, 1f, 1f, 1f);

        // Seçenekler (PauseScreen'deki drawOption kalıbı)
        drawOption(game.bundle.get("death.retry"),    cx, cy - 40f,  0);
        drawOption(game.bundle.get("death.mainmenu"), cx, cy - 100f, 1);

        // İpucu
        fontHint.setColor(1f, 1f, 1f, 0.4f * fadeAlpha);
        //String hintText = game.bundle.get("death.hint");   "
        //layout.setText(fontHint, hintText);
        //fontHint.draw(batch, hintText, cx - layout.width / 2f, cy - 155f);
        fontHint.setColor(1f, 1f, 1f, 1f);

        batch.end();

        handleInput();
    }


    private void drawOption(String label, float cx, float y, int index) {
        if (selectedOption == index) {
            fontMenu.setColor(1f, 0.25f, 0.15f, fadeAlpha * selectionBlink);
            layout.setText(fontMenu, "> " + label + " <");
            fontMenu.draw(batch, "> " + label + " <", cx - layout.width / 2f, y);
        } else {
            fontMenu.setColor(1f, 1f, 1f, fadeAlpha * 0.7f);
            layout.setText(fontMenu, label);
            fontMenu.draw(batch, label, cx - layout.width / 2f, y);
        }
        fontMenu.setColor(1f, 1f, 1f, 1f);
    }

    private void updateAnimations(float delta) {
        fadeAlpha      = Math.min(fadeAlpha + delta * 1.6f, 1f);
        bgRedIntensity = Math.min(bgRedIntensity + delta * 0.25f, 0.18f);
        titlePulse    += delta * 2.5f;
        titleScale     = MathUtils.sin(titlePulse) * 0.5f + 0.5f;
        selectionBlink = (float)(Math.sin(selectionBlink + delta * 5f) * 0.25f + 0.75f);
    }

    private void drawParticles(float delta) {
        Random rand = new Random();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleY[i] -= particleSpeed[i] * delta;
            if (particleY[i] < -10f) {
                particleY[i] = UI_HEIGHT + 10f;
                particleX[i] = rand.nextFloat() * UI_WIDTH;
            }
            shapeRenderer.setColor(
                    0.75f + rand.nextFloat() * 0.2f,
                    0f,
                    0f,
                    particleAlpha[i] * fadeAlpha
            );
            shapeRenderer.circle(particleX[i], particleY[i], particleSize[i]);
        }
        shapeRenderer.end();
    }


    private void handleInput() {
        Controller c = Controllers.getControllers().size > 0
                ? Controllers.getControllers().first() : null;

        float   stickY    = (c != null) ? c.getAxis(GAMEPAD_AXIS_LEFT_Y) : 0f;
        boolean stickUp   = stickY < -DEADZONE;
        boolean stickDown = stickY >  DEADZONE;

        boolean up = Gdx.input.isKeyJustPressed(Input.Keys.UP)
                || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || (stickUp && !prevStickUp);
        boolean down = Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
                || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || (stickDown && !prevStickDown);
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || (c != null && c.getButton(GAMEPAD_BUTTON_A) && !prevButtonA);

        if (up   && selectedOption > 0)                selectedOption--;
        if (down && selectedOption < OPTION_COUNT - 1) selectedOption++;

        if (confirm) {
            switch (selectedOption) {
                case 0:
                    // respawn
                    dispose();
                    game.setScreen(new GameScreen(game));
                    break;
                case 1:
                    // main menu
                    dispose();
                    game.setScreen(new MainMenuScreen(game));
                    break;
            }
        }

        if (c != null) {
            prevButtonA   = c.getButton(GAMEPAD_BUTTON_A);
            prevStickUp   = stickUp;
            prevStickDown = stickDown;
        } else {
            prevButtonA = prevStickUp = prevStickDown = false;
        }
    }


    @Override public void show()   {}
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(UI_WIDTH / 2f, UI_HEIGHT / 2f, 0);
        camera.update();
        initParticles();
    }

    @Override
    public void dispose() {
        GameScreen.setScore(0);
        batch.dispose();
        shapeRenderer.dispose();
    }
}