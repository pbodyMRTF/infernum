import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

public class AboutScreen implements Screen {
    final Jgame game;
    SpriteBatch batch;
    ShapeRenderer shapeRenderer;
    BitmapFont font;
    private Texture MRTFTEX;

    private float menuAlpha      = 0f;
    private float backgroundHue  = 0f;
    private float selectionBlink = 0f;
    private float scrollOffset   = 0f;
    private float photoScale     = 0f;

    private GlyphLayout layout = new GlyphLayout();

    private boolean linkOpened      = false;
    private float   linkMessageAlpha = 0f;

    public AboutScreen(final Jgame game) {
        this.game         = game;
        this.batch        = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.font         = game.font;
        loadAssets();
    }

    @Override
    public void render(float delta) {
        updateAnimations(delta);

        Color bgColor = new Color();
        bgColor.fromHsv(backgroundHue, 0.6f, 0.3f);
        Gdx.gl.glClearColor(bgColor.r, bgColor.g, bgColor.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();

        drawDecorations(w, h);

        batch.begin();

        font.getData().setScale(2.0f);
        font.setColor(1, 0.3f, 0.2f, menuAlpha);
        font.draw(batch, game.bundle.get("about.title"), w / 2f - 120, h - 60);
        font.getData().setScale(1f);

        float leftX = w / 4f;
        float yPos  = h - 150;

        font.getData().setScale(1.5f);
        font.setColor(1, 1, 1, menuAlpha * 0.9f);
        font.draw(batch, game.bundle.get("about.developer"), leftX - 150, yPos);
        font.getData().setScale(1f);

        yPos -= 60;

        if (MRTFTEX != null) {
            float photoSize = 100 * photoScale;
            float photoX    = leftX - photoSize / 2 - 10;
            float photoY    = yPos - photoSize - 10;
            batch.setColor(1, 1, 1, menuAlpha * photoScale);
            batch.draw(MRTFTEX, photoX, photoY, photoSize, photoSize);
            batch.setColor(1, 1, 1, 1);
        }

        yPos -= 130;

        font.getData().setScale(1.3f);
        font.setColor(0.8f, 0.8f, 1f, menuAlpha * 0.85f);
        font.draw(batch, game.bundle.get("about.developer.name"), leftX - 100, yPos);
        font.getData().setScale(1f);

        yPos -= 40;
        font.getData().setScale(0.85f);
        font.setColor(0.7f, 0.7f, 0.7f, menuAlpha * 0.7f);
        font.draw(batch, game.bundle.get("about.developer.role"), leftX - 100, yPos);
        yPos -= 25;
        font.setColor(0.6f, 0.6f, 0.6f, menuAlpha * 0.65f);
        font.draw(batch, game.bundle.get("about.developer.note"), leftX - 130, yPos);
        font.getData().setScale(1f);

        float rightX = w * 3f / 4f;
        yPos = h - 150;

        font.getData().setScale(1.5f);
        font.setColor(1, 1, 1, menuAlpha * 0.9f);
        font.draw(batch, game.bundle.get("about.assets"), rightX - 90, yPos);
        font.getData().setScale(1f);

        yPos -= 60;
        font.getData().setScale(1.1f);
        font.setColor(0.9f, 0.9f, 0.5f, menuAlpha * 0.8f);
        font.draw(batch, game.bundle.get("about.assets.tileset"), rightX - 70, yPos);
        font.getData().setScale(1f);

        yPos -= 40;
        font.getData().setScale(0.9f);
        font.setColor(0.8f, 0.8f, 0.8f, menuAlpha * 0.75f);
        font.draw(batch, game.bundle.get("about.assets.tileset.name"),  rightX - 100, yPos); yPos -= 25;
        font.draw(batch, game.bundle.get("about.assets.tileset.line2"), rightX - 100, yPos); yPos -= 25;
        font.draw(batch, game.bundle.get("about.assets.tileset.line3"), rightX - 100, yPos);

        yPos -= 40;
        font.getData().setScale(0.69f);
        font.setColor(0.5f, 0.7f, 1f, menuAlpha * 0.7f * selectionBlink);
        String linkText  = "https://free-game-assets.itch.io/free-cursed-land-top-down-pixel-art-tileset";
        float  linkTextX = rightX - 400;
        float  linkTextY = yPos;
        font.draw(batch, linkText, linkTextX, linkTextY);

        font.setColor(0.8f, 0.8f, 0.8f, menuAlpha * 0.75f);
        font.draw(batch, game.bundle.get("about.assets.font"), rightX - 120, yPos - 300);

        layout.setText(font, linkText);
        float linkWidth  = layout.width;
        float linkHeight = layout.height;

        if (linkOpened && linkMessageAlpha > 0) {
            font.getData().setScale(0.9f);
            font.setColor(0.3f, 1f, 0.3f, linkMessageAlpha);
            font.draw(batch, game.bundle.get("about.link.message"), linkTextX, linkTextY - 25);
        }

        font.getData().setScale(1f);
        batch.end();

        if (Gdx.input.justTouched()) {
            float mouseX = Gdx.input.getX();
            float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
            if (mouseX >= linkTextX && mouseX <= linkTextX + linkWidth &&
                    mouseY >= linkTextY - linkHeight && mouseY <= linkTextY) {
                OpenLink(linkText);
                linkOpened       = true;
                linkMessageAlpha = 1f;
            }
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1, 1, 1, 0.15f * menuAlpha);
        shapeRenderer.rectLine(w / 2f, h - 140, w / 2f, 100, 2);
        shapeRenderer.end();

        batch.begin();
        font.setColor(0.7f, 0.7f, 0.7f, menuAlpha * 0.6f);
        font.getData().setScale(0.8f);
        font.draw(batch, "" + Jgame.Version, 20, 60);
        font.setColor(1f, 1f, 1f, menuAlpha * selectionBlink);
        font.draw(batch, game.bundle.get("about.back"), 20, 30);
        font.getData().setScale(1f);
        batch.end();

        handleInput();
    }

    public static void OpenLink(String url) { Gdx.net.openURI(url); }

    private void loadAssets() {
        try { MRTFTEX = Assets.getTexture(Assets.Textures.MRTF); }
        catch (Exception e) { MRTFTEX = null; }
    }

    private void updateAnimations(float delta) {
        menuAlpha      = Math.min(menuAlpha + delta * 1.2f, 1f);
        backgroundHue  = (backgroundHue + delta * 20f) % 360f;
        selectionBlink = MathUtils.sin(Gdx.graphics.getFrameId() * 0.1f) * 0.3f + 0.7f;
        photoScale     = Math.min(photoScale + delta * 1.5f, 1f);
        scrollOffset  += delta * 10f;
        if (linkOpened && linkMessageAlpha > 0)
            linkMessageAlpha = Math.max(linkMessageAlpha - delta * 0.1f, 0f);
    }

    private void drawDecorations(int w, int h) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 1, 1, 0.2f);
        float padding = 30;
        shapeRenderer.rect(padding, padding, w - padding * 2, h - padding * 2);
        shapeRenderer.end();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
                Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            game.setScreen(new MainMenuScreen(game));
        }
    }

    @Override public void show() {}
    @Override public void resize(int width, int height) {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }
}