import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.util.Random;

public class MainMenuScreen implements Screen {

    final Jgame game;
    SpriteBatch batch;
    ShapeRenderer shapeRenderer;
    BitmapFont font;

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private static final float VIRTUAL_WIDTH  = 1024f;
    private static final float VIRTUAL_HEIGHT = 768f;

    private float titleScale    = 1.0f;
    private float titlePulse    = 0f;
    private float menuAlpha     = 0f;
    private float backgroundHue = 0f;
    private int   selectedOption = 0;
    private float selectionBlink = 0f;

    private static final int PARTICLE_COUNT = 50;
    private float[] particleX     = new float[PARTICLE_COUNT];
    private float[] particleY     = new float[PARTICLE_COUNT];
    private float[] particleSpeed = new float[PARTICLE_COUNT];
    private float[] particleSize  = new float[PARTICLE_COUNT];

    private static final int   MENU_OPTION_COUNT  = 5;
    private static final int   GAMEPAD_BUTTON_A   = 0;
    private static final int   GAMEPAD_BUTTON_B   = 1;
    private static final int   GAMEPAD_AXIS_LEFT_Y = 1;
    private static final float DEADZONE           = 0.5f;

    private boolean prevButtonA   = false;
    private boolean prevButtonB   = false;
    private boolean prevStickUp   = false;
    private boolean prevStickDown = false;

    public MainMenuScreen(final Jgame game) {
        this.game = game;
        batch        = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font         = game.font;

        camera = new OrthographicCamera();
        viewport = new ExtendViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();

        initParticles();
    }

    private Controller getGamepad() {
        if (Controllers.getControllers().size > 0) return Controllers.getControllers().first();
        return null;
    }

    private boolean gamepadJustPressed(Controller c, int button, boolean prevState) {
        return c != null && c.getButton(button) && !prevState;
    }

    private void initParticles() {
        Random rand = new Random();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i]     = rand.nextFloat() * VIRTUAL_WIDTH;
            particleY[i]     = rand.nextFloat() * VIRTUAL_HEIGHT;
            particleSpeed[i] = 20 + rand.nextFloat() * 40;
            particleSize[i]  = 2  + rand.nextFloat() * 4;
        }
    }

    @Override
    public void render(float delta) {
        updateAnimations(delta);

        Color bgColor = new Color();
        bgColor.fromHsv(backgroundHue % 360, 0.6f, 0.3f);
        Gdx.gl.glClearColor(bgColor.r, bgColor.g, bgColor.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        drawDecorations();
        drawParticles(delta);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        font.getData().setScale(2.3f + titleScale * 0.1f);
        font.setColor(1, 0.3f, 0.2f, 1);
        font.draw(batch, game.bundle.get("menu.title"), VIRTUAL_WIDTH / 2f - 100, VIRTUAL_HEIGHT / 2f + 100);
        font.setColor(1, 1, 1, 1);
        font.getData().setScale(1f);

        float menuX      = VIRTUAL_WIDTH / 2f - 100;
        float menuStartY = VIRTUAL_HEIGHT / 2f;
        float spacing    = 50f;

        drawMenuItem(game.bundle.get("menu.start"),    menuX, menuStartY,              0);
        drawMenuItem(game.bundle.get("menu.tutorial"), menuX, menuStartY - spacing,    1);
        drawMenuItem(game.bundle.get("menu.settings"), menuX, menuStartY - 2 * spacing, 2);
        drawMenuItem(game.bundle.get("menu.credits"),  menuX, menuStartY - 3 * spacing, 3);
        drawMenuItem(game.bundle.get("menu.exit"),     menuX, menuStartY - 4 * spacing, 4);

        font.setColor(0.7f, 0.7f, 0.7f, menuAlpha * 0.6f);
        font.getData().setScale(0.7f);
        font.draw(batch, game.bundle.format("menu.version", Jgame.Version), 10, 30);
        font.getData().setScale(1f);

        batch.end();
        handleInput();
    }

    private void drawMenuItem(String label, float x, float y, int index) {
        if (selectedOption == index) {
            font.setColor(1, 1, 0, menuAlpha * selectionBlink);
            font.draw(batch, "> " + label + " <", x, y);
        } else {
            font.setColor(1, 1, 1, menuAlpha);
            font.draw(batch, "  " + label, x, y);
        }
    }

    private void updateAnimations(float delta) {
        titlePulse    += delta * 3f;
        titleScale     = MathUtils.sin(titlePulse) * 0.5f + 0.5f;
        menuAlpha      = Math.min(menuAlpha + delta * 1.2f, 1f);
        backgroundHue  = 330 + (MathUtils.sin(titlePulse * 0.1f) * 0.5f + 0.5f) * 60f;
        selectionBlink += delta * 4f;
        selectionBlink = MathUtils.sin(selectionBlink) * 0.3f + 0.7f;
    }

    private void drawParticles(float delta) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleY[i] -= particleSpeed[i] * delta;
            if (particleY[i] < -10) {
                particleY[i] = VIRTUAL_HEIGHT + 10;
                particleX[i] = new Random().nextFloat() * VIRTUAL_WIDTH;
            }
            shapeRenderer.setColor(0.58f, 0.6f, 0.62f, 0.2f);
            shapeRenderer.circle(particleX[i], particleY[i], particleSize[i]);
        }
        shapeRenderer.end();
    }

    private void drawDecorations() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 1, 1, 0.2f);
        float padding    = 30;
        float cornerSize = 20;
        shapeRenderer.rect(padding, padding, VIRTUAL_WIDTH - padding * 2, VIRTUAL_HEIGHT - padding * 2);
        shapeRenderer.line(padding, VIRTUAL_HEIGHT - padding, padding + cornerSize, VIRTUAL_HEIGHT - padding);
        shapeRenderer.line(padding, VIRTUAL_HEIGHT - padding, padding, VIRTUAL_HEIGHT - padding - cornerSize);
        shapeRenderer.line(VIRTUAL_WIDTH - padding, VIRTUAL_HEIGHT - padding, VIRTUAL_WIDTH - padding - cornerSize, VIRTUAL_HEIGHT - padding);
        shapeRenderer.line(VIRTUAL_WIDTH - padding, VIRTUAL_HEIGHT - padding, VIRTUAL_WIDTH - padding, VIRTUAL_HEIGHT - padding - cornerSize);
        shapeRenderer.line(padding, padding, padding + cornerSize, padding);
        shapeRenderer.line(padding, padding, padding, padding + cornerSize);
        shapeRenderer.line(VIRTUAL_WIDTH - padding, padding, VIRTUAL_WIDTH - padding - cornerSize, padding);
        shapeRenderer.line(VIRTUAL_WIDTH - padding, padding, VIRTUAL_WIDTH - padding, padding + cornerSize);
        shapeRenderer.end();
    }

    private void handleInput() {
        Controller c = getGamepad();

        float stickY      = (c != null) ? c.getAxis(GAMEPAD_AXIS_LEFT_Y) : 0f;
        boolean stickUp   = stickY < -DEADZONE;
        boolean stickDown = stickY >  DEADZONE;

        boolean navigateUp   = Gdx.input.isKeyJustPressed(Input.Keys.UP)   || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || (stickUp && !prevStickUp);
        boolean navigateDown = Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || (stickDown && !prevStickDown);
        boolean confirm      = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_A, prevButtonA);

        if (navigateUp   && selectedOption > 0)                  selectedOption--;
        if (navigateDown && selectedOption < MENU_OPTION_COUNT - 1) selectedOption++;

        if (confirm) {
            switch (selectedOption) {
                case 0: game.setScreen(new GameScreen(game));    dispose(); break;
                case 1: game.setScreen(new TutorialScreen(game)); dispose(); break;
                case 2: game.setScreen(new SettingsScreen(game)); dispose(); break;
                case 3: game.setScreen(new AboutScreen(game));   dispose(); break;
                case 4: dispose(); Gdx.app.exit(); break;
            }
        }

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) Gdx.app.exit();

        if (c != null) {
            prevButtonA   = c.getButton(GAMEPAD_BUTTON_A);
            prevButtonB   = c.getButton(GAMEPAD_BUTTON_B);
            prevStickUp   = stickUp;
            prevStickDown = stickDown;
        } else {
            prevButtonA = prevButtonB = prevStickUp = prevStickDown = false;
        }
    }

    @Override public void show() {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();
        initParticles();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }
}