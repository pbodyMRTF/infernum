import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class OnlineLobbyScreen implements Screen {

    private static final float UI_WIDTH  = 1024f;
    private static final float UI_HEIGHT = 768f;

    private final Jgame game;
    private SpriteBatch batch;
    private BitmapFont font;
    private OrthographicCamera camera;
    private ExtendViewport viewport;

    private StringBuilder ipInput = new StringBuilder("localhost");
    private boolean connecting = false;
    private String statusMessage = "";

    public OnlineLobbyScreen(Jgame game) {
        this.game = game;
        batch  = new SpriteBatch();
        font   = game.getFont(Jgame.FONT_SIZE_32);
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(UI_WIDTH, UI_HEIGHT, camera);
        camera.position.set(UI_WIDTH / 2, UI_HEIGHT / 2, 0);
        camera.update();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        handleInput();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.draw(batch, "Sunucu IP:", 300, 450);
        font.draw(batch, ipInput.toString() + "_", 300, 400);
        font.draw(batch, "[ENTER] Bağlan   [ESC] Geri", 300, 350);
        if (!statusMessage.isEmpty()) {
            font.draw(batch, statusMessage, 300, 300);
        }
        batch.end();
    }

    private void handleInput() {
        if (connecting) return;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && ipInput.length() > 0) {
            ipInput.deleteCharAt(ipInput.length() - 1);
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            startConnection();
            return;
        }

        // Basit karakter yakalama (rakam, nokta, harf)
        for (int key = Input.Keys.A; key <= Input.Keys.Z; key++) {
            if (Gdx.input.isKeyJustPressed(key)) {
                ipInput.append(Input.Keys.toString(key).toLowerCase());
            }
        }
        for (int key = Input.Keys.NUM_0; key <= Input.Keys.NUM_9; key++) {
            if (Gdx.input.isKeyJustPressed(key)) {
                ipInput.append(Input.Keys.toString(key));
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) {
            ipInput.append(".");
        }
    }

    private void startConnection() {
        connecting = true;
        statusMessage = "Bağlanılıyor...";
        game.setScreen(new OnlineGameScreen(game, ipInput.toString().trim()));
    }

    @Override public void show() {}
    @Override public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(UI_WIDTH / 2, UI_HEIGHT / 2, 0);
        camera.update();
    }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
    }
}