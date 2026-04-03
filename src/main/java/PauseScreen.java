import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class PauseScreen implements Screen {

    private static final float UI_WIDTH  = 1920f;
    private static final float UI_HEIGHT = 1080f;

    private static final int GAMEPAD_BUTTON_A = 0;
    private static final int GAMEPAD_AXIS_LEFT_Y = 1;
    private static final float DEADZONE = 0.5f;

    private final Jgame    game;
    private final Screen   gameScreen;

    private SpriteBatch    batch;
    private BitmapFont     fontTitle;
    private BitmapFont     fontMenu;
    private BitmapFont     fontHint;

    private OrthographicCamera camera;
    private ExtendViewport      viewport;

    // Blur için: oyun ekranını bir texture'a çek
    private final Texture  frozenFrame;  // dışarıdan geçirilir
    private ShaderProgram  blurShader;

    // Menü durumu
    private int   selectedOption = 0;   // 0 = Devam Et, 1 = Ana Menü
    private float selectionBlink = 0f;
    private float fadeAlpha      = 0f;  // fade-in

    private boolean prevButtonA   = false;
    private boolean prevStickUp   = false;
    private boolean prevStickDown = false;

    private GlyphLayout layout = new GlyphLayout();

    // Panel boyutları
    private static final float PANEL_W = 320f;
    private static final float PANEL_H = 240f;

    /**
     * @param frozenFrame  GameScreen render'ından alınan screenshot texture'ı.
     *                     null geçilirse blur arka plan olmadan düz karartma kullanılır.
     */
    public PauseScreen(Jgame game, Screen gameScreen, Texture frozenFrame) {
        this.game        = game;
        this.gameScreen  = gameScreen;
        this.frozenFrame = frozenFrame;

        batch     = new SpriteBatch();
        fontTitle = game.getFont(Jgame.FONT_SIZE_64);
        fontMenu  = game.getFont(Jgame.FONT_SIZE_32);
        fontHint  = game.getFont(Jgame.FONT_SIZE_16);

        camera   = new OrthographicCamera();
        viewport = new ExtendViewport(UI_WIDTH, UI_HEIGHT, camera);
        camera.position.set(UI_WIDTH / 2f, UI_HEIGHT / 2f, 0);
        camera.update();

        loadBlurShader();
    }

    private void loadBlurShader() {
        ShaderProgram.pedantic = false;
        blurShader = new ShaderProgram(
                Gdx.files.internal("shaders/pause_blur.vsh"),
                Gdx.files.internal("shaders/pause_blur.fsh")
        );
        if (!blurShader.isCompiled()) {
            Gdx.app.error("PauseScreen", "Blur shader hata: " + blurShader.getLog());
            blurShader.dispose();
            blurShader = null;
        }
    }

    @Override
    public void render(float delta) {
        fadeAlpha      = Math.min(fadeAlpha + delta * 4f, 1f);
        selectionBlink = (float)(Math.sin(selectionBlink + delta * 5f) * 0.25f + 0.75f);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // --- Blur'lu arka plan ---
        if (frozenFrame != null && blurShader != null && blurShader.isCompiled()) {
            batch.setShader(blurShader);
            blurShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            batch.draw(frozenFrame, 0, 0, UI_WIDTH, UI_HEIGHT,
                    0, 0, frozenFrame.getWidth(), frozenFrame.getHeight(), false, true);
            batch.setShader(null);
        } else {
            // Shader yoksa sade karartma
            batch.setColor(0, 0, 0, 0.7f * fadeAlpha);
            // boş bir fill yok SpriteBatch'te; frozenFrame null ise şeffaf bırak
            batch.setColor(1, 1, 1, 1);
        }

        float cx = UI_WIDTH  / 2f;
        float cy = UI_HEIGHT / 2f;

        // --- Başlık ---
        fontTitle.setColor(1f, 1f, 1f, fadeAlpha);
        layout.setText(fontTitle, game.bundle.get("pause.title"));
        fontTitle.draw(batch, game.bundle.get("pause.title"),
                cx - layout.width / 2f, cy + 100);

        // Seçenekler
        drawOption(game.bundle.get("pause.resume"),   cx, cy + 10,  0);
        drawOption(game.bundle.get("pause.mainmenu"), cx, cy - 50,  1);

        fontHint.setColor(1f, 1f, 1f, 0.45f * fadeAlpha);
        layout.setText(fontHint, game.bundle.get("pause.hint"));
        fontHint.draw(batch, game.bundle.get("pause.hint"),
                cx - layout.width / 2f, cy - 110);
        fontHint.setColor(1, 1, 1, 1);

        batch.end();
        handleInput();
    }

    private void drawOption(String label, float cx, float y, int index) {
        boolean sel = (selectedOption == index);
        if (sel) {
            fontMenu.setColor(1f, 0.9f, 0.3f, fadeAlpha * selectionBlink);
            layout.setText(fontMenu, "> " + label + " <");
            fontMenu.draw(batch, "> " + label + " <", cx - layout.width / 2f, y);
        } else {
            fontMenu.setColor(1f, 1f, 1f, fadeAlpha * 0.75f);
            layout.setText(fontMenu, label);
            fontMenu.draw(batch, label, cx - layout.width / 2f, y);
        }
        fontMenu.setColor(1, 1, 1, 1);
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

        // ESC direkt olarak devam et
        boolean resume = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE);

        if (up   && selectedOption > 0) selectedOption--;
        if (down && selectedOption < 1) selectedOption++;

        if (resume || (confirm && selectedOption == 0)) {
            game.setScreen(gameScreen);
        } else if (confirm && selectedOption == 1) {
            dispose();
            game.setScreen(new MainMenuScreen(game));
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
    }

    @Override
    public void dispose() {
        batch.dispose();
        if (blurShader != null) blurShader.dispose();
        // frozenFrame'i burada dispose etme — GameScreen'e ait olabilir
    }
}