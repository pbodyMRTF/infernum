package io.github.pbodyMRTF.infernum;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class OnlineLobbyScreen implements Screen {

    final Jgame game;
    SpriteBatch batch;
    ShapeRenderer shapeRenderer;
    BitmapFont font;

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private static final float VIRTUAL_WIDTH  = 1024f;
    private static final float VIRTUAL_HEIGHT = 768f;

    private Sound Select;
    private Sound ConfirmSound;

    private float menuAlpha      = 0f;
    private float backgroundHue  = 0f;
    private float selectionBlink = 0f;

    private StringBuilder ipInput = new StringBuilder("localhost");
    private boolean connecting    = false;
    private String  statusMessage = "";

    public OnlineLobbyScreen(final Jgame game) {
        this.game          = game;
        this.batch         = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.font          = game.getFont(Jgame.FONT_SIZE_32);

        camera   = new OrthographicCamera();
        viewport = new ExtendViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();
        loadAssets();
    }

    private void loadAssets() {
        Select        = Assets.getSound(Assets.Sounds.SELECT);
        ConfirmSound  = Assets.getSound(Assets.Sounds.CONFIRM);
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
        font.draw(batch, "SUNUCUYA BAĞLAN", VIRTUAL_WIDTH / 2f - 160, VIRTUAL_HEIGHT - 100);
        font.getData().setScale(1f);

        float centerX = VIRTUAL_WIDTH / 2f - 150;
        float labelY  = VIRTUAL_HEIGHT / 2f + 100;

        font.setColor(1, 1, 1, menuAlpha);
        font.draw(batch, "Sunucu IP:", centerX, labelY);

        // Giriş alanı, seçili menü öğesi gibi vurgulanır ve yanıp söner
        font.setColor(1, 1, 0, menuAlpha * selectionBlink);
        font.draw(batch, "> " + ipInput.toString() + "_ <", centerX, labelY - 60);

        font.setColor(1, 1, 1, menuAlpha);
        font.draw(batch, "[ENTER] Bağlan   [ESC] Geri", centerX, labelY - 140);

        if (!statusMessage.isEmpty()) {
            font.setColor(0.6f, 1f, 0.6f, menuAlpha);
            font.draw(batch, statusMessage, centerX, labelY - 200);
        }

        font.setColor(0.7f, 0.7f, 0.7f, menuAlpha * 0.6f);
        font.getData().setScale(0.8f);
        font.draw(batch, "" + Jgame.Version, 20, 40);
        font.getData().setScale(1f);

        batch.end();

        handleInput();
    }

    private void updateAnimations(float delta) {
        menuAlpha      = Math.min(menuAlpha + delta * 1.2f, 1f);
        backgroundHue  = (backgroundHue + delta * 20f) % 360f;
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
        if (connecting) return;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Select.play();
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

        boolean ctrlDown = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

        // Ctrl+V: panodaki metni yapıştır
        if (ctrlDown && Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            String clipboard = Gdx.app.getClipboard().getContents();
            if (clipboard != null) {
                for (int i = 0; i < clipboard.length(); i++) {
                    char c = clipboard.charAt(i);
                    if (c >= 32 && c < 127) {
                        ipInput.append(c);
                    }
                }
            }
            return;
        }

        // Ctrl+C: mevcut girdiyi panoya kopyala
        if (ctrlDown && Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            Gdx.app.getClipboard().setContents(ipInput.toString());
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
        ConfirmSound.play();
        connecting    = true;
        statusMessage = "Bağlanılıyor...";
        game.setScreen(new OnlineGameScreen(game, ipInput.toString().trim()));
    }

    @Override public void show()   {}
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }
}