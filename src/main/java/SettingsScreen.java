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

public class SettingsScreen implements Screen {

    final Jgame game;
    SpriteBatch batch;
    ShapeRenderer shapeRenderer;
    BitmapFont font;

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private static final float VIRTUAL_WIDTH = 1024f;
    private static final float VIRTUAL_HEIGHT = 768f;

    private float menuAlpha = 0f;
    private float backgroundHue = 0f;
    private int selectedOption = 0;
    private float selectionBlink = 0f;

    private String[] options = {"Müzik: AÇIK", "Zorluk: NORMAL", "Ekran: PENCERE", "Geri Dön"};

    private static final int GAMEPAD_BUTTON_A    = 0;
    private static final int GAMEPAD_BUTTON_B    = 1;
    private static final int GAMEPAD_AXIS_LEFT_Y = 1;
    private static final float DEADZONE          = 0.5f;

    private boolean prevButtonA   = false;
    private boolean prevButtonB   = false;
    private boolean prevStickUp   = false;
    private boolean prevStickDown = false;

    public SettingsScreen(final Jgame game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.font = game.font;

        camera = new OrthographicCamera();
        viewport = new ExtendViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();

        GameConfig config = ConfigManager.loadConfig();

        options[0] = config.music ? "Müzik: AÇIK" : "Müzik: KAPALI";
        options[1] = "Zorluk: " + config.difficulty;
        options[2] = "Ekran: " + (config.Screen.equals("FULLSCREEN") ? "TAM EKRAN" : "PENCERE");
        options[3] = "Geri Dön";
    }

    private Controller getGamepad() {
        if (Controllers.getControllers().size > 0) {
            return Controllers.getControllers().first();
        }
        return null;
    }

    private boolean gamepadJustPressed(Controller c, int button, boolean prevState) {
        return c != null && c.getButton(button) && !prevState;
    }

    @Override
    public void render(float delta) {
        updateAnimations(delta);

        Color bgColor = new Color();
        bgColor.fromHsv(backgroundHue, 0.6f, 0.3f);
        Gdx.gl.glClearColor(bgColor.r, bgColor.g, bgColor.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();

        shapeRenderer.setProjectionMatrix(camera.combined);
        drawDecorations();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        font.getData().setScale(2.0f);
        font.setColor(1, 0.3f, 0.2f, menuAlpha);
        font.draw(batch, "AYARLAR", VIRTUAL_WIDTH / 2f - 100, VIRTUAL_HEIGHT - 100);
        font.getData().setScale(1f);

        float menuStartY = VIRTUAL_HEIGHT / 2f + 50;
        float menuX = VIRTUAL_WIDTH / 2f - 150;

        for (int i = 0; i < options.length; i++) {
            if (i == selectedOption) {
                font.setColor(1, 1, 0, menuAlpha * selectionBlink);
                font.draw(batch, "> " + options[i] + " <", menuX, menuStartY - (i * 60));
            } else {
                font.setColor(1, 1, 1, menuAlpha);
                font.draw(batch, "  " + options[i], menuX, menuStartY - (i * 60));
            }
        }

        font.setColor(0.7f, 0.7f, 0.7f, menuAlpha * 0.6f);
        font.getData().setScale(0.8f);
        font.draw(batch, "" + Jgame.Version, 20, 40);
        font.getData().setScale(1f);

        batch.end();

        handleInput();
    }

    private void updateAnimations(float delta) {
        menuAlpha = Math.min(menuAlpha + delta * 1.2f, 1f);
        backgroundHue = (backgroundHue + delta * 20f) % 360f;
        selectionBlink = MathUtils.sin(Gdx.graphics.getFrameId() * 0.1f) * 0.3f + 0.7f;
    }

    private void drawDecorations() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 1, 1, 0.2f);

        float padding = 30;
        shapeRenderer.rect(padding, padding, VIRTUAL_WIDTH - padding * 2, VIRTUAL_HEIGHT - padding * 2);

        shapeRenderer.end();
    }

    private void handleInput() {
        Controller c = getGamepad();

        float stickY = (c != null) ? c.getAxis(GAMEPAD_AXIS_LEFT_Y) : 0f;
        boolean stickUp   = stickY < -DEADZONE;
        boolean stickDown = stickY >  DEADZONE;

        boolean navigateUp   = Gdx.input.isKeyJustPressed(Input.Keys.UP)   || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || (stickUp && !prevStickUp);
        boolean navigateDown = Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || (stickDown && !prevStickDown);
        boolean confirm      = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_A, prevButtonA);
        boolean back         = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || gamepadJustPressed(c, GAMEPAD_BUTTON_B, prevButtonB);

        if (navigateUp && selectedOption > 0) selectedOption--;
        if (navigateDown && selectedOption < options.length - 1) selectedOption++;

        if (confirm) {
            if (selectedOption == 0) {
                GameConfig config = ConfigManager.getConfig();
                config.music = !config.music;
                ConfigManager.saveConfig(config);
                options[0] = config.music ? "Müzik: AÇIK" : "Müzik: KAPALI";

            } else if (selectedOption == 1) {
                GameConfig config = ConfigManager.getConfig();

                if (config.difficulty.equals("NORMAL")) {
                    config.difficulty = "ZOR";
                } else if (config.difficulty.equals("ZOR")) {
                    config.difficulty = "Ben Erlik Han'ım";
                } else if (config.difficulty.equals("Ben Erlik Han'ım")) {
                    config.difficulty = "NORMAL";
                }

                ConfigManager.saveConfig(config);
                options[1] = "Zorluk: " + config.difficulty;

            } else if (selectedOption == 2) {
                GameConfig config = ConfigManager.getConfig();
                if (config.Screen.equals("FULLSCREEN")) {
                    Gdx.graphics.setWindowedMode(1280, 720);
                    config.Screen = "WINDOW";
                } else {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                    config.Screen = "FULLSCREEN";
                }
                ConfigManager.saveConfig(config);
                options[2] = "Ekran: " + (config.Screen.equals("FULLSCREEN") ? "TAM EKRAN" : "PENCERE");

            } else if (selectedOption == 3) {
                game.setScreen(new MainMenuScreen(game));
            }
        }

        if (back) {
            game.setScreen(new MainMenuScreen(game));
        }

        if (c != null) {
            prevButtonA   = c.getButton(GAMEPAD_BUTTON_A);
            prevButtonB   = c.getButton(GAMEPAD_BUTTON_B);
            prevStickUp   = stickUp;
            prevStickDown = stickDown;
        } else {
            prevButtonA = prevButtonB = prevStickUp = prevStickDown = false;
        }
    }

    @Override
    public void show() {}

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }
}