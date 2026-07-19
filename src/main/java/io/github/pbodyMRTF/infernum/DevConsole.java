package io.github.pbodyMRTF.infernum;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DevConsole {

    private final SpriteBatch    batch;
    private final ShapeRenderer  shapeRenderer;

    private final BitmapFont fontTitle;
    private final BitmapFont fontBody;
    private final BitmapFont fontSmall;

    private final OrthographicCamera camera;
    private final ExtendViewport      viewport;
    private static final float VIRTUAL_WIDTH  = 1024f;
    private static final float VIRTUAL_HEIGHT = 768f;

    private boolean open = false;
    private float   animTime      = 0f;
    private float   backgroundHue = 0f;

    private final StringBuilder inputLine = new StringBuilder();
    private final List<String>  history   = new ArrayList<>();
    private static final int    MAX_VISIBLE_LINES = 18;
    private static final int    MAX_HISTORY_LINES = 300;

    // Tab-complete desteği için bilinen komut listesi
    private static final List<String> COMMAND_NAMES = Arrays.asList(
            "help", "clear", "version", "startgame", "halt", "echo", "hud-scale", "hud-color", "speed"
    );
    private List<String> tabMatches = new ArrayList<>();
    private int          tabIndex   = 0;
    private String       tabBase    = "";

    private final InputAdapter inputProcessor;

    public DevConsole(final Jgame game) {
        this.batch         = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();

        this.fontTitle = game.getFont(Jgame.FONT_SIZE_32);
        this.fontBody  = game.getFont(Jgame.FONT_SIZE_32);
        this.fontSmall = game.getFont(Jgame.FONT_SIZE_16);

        this.camera   = new OrthographicCamera();
        this.viewport = new ExtendViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        camera.position.set(VIRTUAL_WIDTH / 2f, VIRTUAL_HEIGHT / 2f, 0);
        camera.update();

        history.add("Developer console ready. Type 'help' and press Enter.");

        inputProcessor = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                // TAB tuşu keyTyped'da görünmez şekilde ele alınmalı (karakter kodu 9, < 32 olduğu için yoksayılıyordu)
                if (keycode == Input.Keys.TAB) {
                    handleTabComplete();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(char character) {
                // The grave/tilde key is reserved for opening/closing the console
                if (character == '`' || character == '~') {
                    return true;
                }
                if (character == '\r' || character == '\n') {
                    executeCommand(inputLine.toString());
                    inputLine.setLength(0);
                    resetTabState();
                    return true;
                }
                if (character == '\b') {
                    if (inputLine.length() > 0) {
                        inputLine.deleteCharAt(inputLine.length() - 1);
                    }
                    resetTabState();
                    return true;
                }
                if (character >= 32 && character < 127) {
                    inputLine.append(character);
                    resetTabState();
                }
                return true;
            }
        };
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Call once per frame from the owning screen's input handling, before
     * any other input is processed. Toggles the console on GRAVE and
     * returns whether the console is currently open, so the caller can
     * skip its own game/menu input while the console is up.
     */
    public boolean pollToggle() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.GRAVE)) {
            toggle();
        }
        return open;
    }

    public void toggle() {
        open = !open;
        Gdx.input.setInputProcessor(open ? inputProcessor : null);
    }

    public void close() {
        if (open) {
            toggle();
        }
    }

    /**
     * TAB tuşuna her basıldığında çağrılır.
     * - Girdi tek kelimeyse (henüz boşluk yoksa), bilinen komutlar arasından
     *   o kelimeyle başlayanları bulur.
     * - Eşleşme tekse doğrudan tamamlar.
     * - Eşleşme birden fazlaysa, art arda TAB basıldıkça eşleşmeler arasında
     *   sırayla gezinir (cycle).
     */
    private void handleTabComplete() {
        String current = inputLine.toString();

        boolean continuingCycle = !tabMatches.isEmpty()
                && current.equalsIgnoreCase(tabMatches.get((tabIndex - 1 + tabMatches.size()) % tabMatches.size()));

        if (!continuingCycle) {
            // Sadece ilk kelime (komut adı) tamamlanır; argümanlara dokunulmaz
            String[] parts = current.split("\\s+", -1);
            if (parts.length > 1) {
                tabMatches.clear();
                return;
            }

            tabBase = parts.length > 0 ? parts[0].toLowerCase() : "";
            tabMatches = new ArrayList<>();
            for (String cmd : COMMAND_NAMES) {
                if (cmd.startsWith(tabBase)) {
                    tabMatches.add(cmd);
                }
            }
            tabIndex = 0;
        }

        if (tabMatches.isEmpty()) {
            return;
        }

        String chosen = tabMatches.get(tabIndex % tabMatches.size());
        tabIndex++;

        inputLine.setLength(0);
        inputLine.append(chosen);
    }

    private void resetTabState() {
        tabMatches.clear();
        tabIndex = 0;
        tabBase = "";
    }

    private void executeCommand(String raw) {
        String command = raw.trim();
        if (command.isEmpty()) return;

        history.add("> " + command);
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                history.add("Available commands: help, clear, version, startgame, halt, echo, hud-scale, hud-color, speed");
                break;
            case "clear":
                history.clear();
                break;
            case "version":
                history.add("Version: " + Jgame.Version);
                break;
            case "startgame":
                toggle();
                break;
            case "halt":
                Gdx.app.exit();
                break;
            case "echo":
                if (parts.length > 1) {
                    StringBuilder joined = new StringBuilder();
                    for (int i = 1; i < parts.length; i++) {
                        if (i > 1) joined.append(" ");
                        joined.append(parts[i]);
                    }
                    history.add(joined.toString());
                } else {
                    history.add("Usage: echo <text>");
                }
                break;
            case "hud-scale":
                if (parts.length >= 2) {
                    try {
                        float scale = Float.parseFloat(parts[1]);
                        history.add("HUD.hudScale changed to " + scale);
                        HUD.hudScale = scale;
                        if(scale > 2.5 ){
                            history.add("!!Warning!! any number larger than 2.5 is not recommended.");
                        }
                    } catch (NumberFormatException e) {
                        history.add("Invalid number: " + parts[1]);
                    }
                } else {
                    history.add("Usage: hud_scale <number>");
                }
                break;
            case "hud-color":
                if (parts.length >= 2) {
                    boolean success = HUD.setHudColor(parts[1]);
                    if (success) {
                        history.add("HUD.hudcolornum changed to " + HUD.hudcolornum);
                    } else {
                        history.add("Invalid color: " + parts[1]);
                        history.add("(deneyebilecekleriniz: WHITE, BLACK, RED, GREEN, BLUE, YELLOW, ORANGE, PINK, GRAY, CYAN, MAGENTA, PURPLE,");
                        history.add("BROWN, MAROON, GOLD, OLIVE, LIME, NAVY)");
                    }
                } else {
                    history.add("Usage: hud-color <color name>");
                }
                break;
            case "speed":
                if (parts.length >= 2) {
                    try {
                        float speed = Float.parseFloat(parts[1]);
                        history.add("Player speed changed to " + speed);
                        Player.speedboost = speed;
                    } catch (NumberFormatException e) {
                        history.add("Invalid number: " + parts[1]);
                    }
                } else {
                    history.add("Usage: speed <number>");
                }
                break;
            default:
                history.add("Unknown command: " + cmd);
                break;
        }

        while (history.size() > MAX_HISTORY_LINES) {
            history.remove(0);
        }
    }

    public void render(float delta) {
        if (!open) return;

        animTime      += delta;
        backgroundHue  = (backgroundHue + delta * 15f) % 360f;

        camera.update();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Color bg = new Color();
        bg.fromHsv(backgroundHue, 0.5f, 0.12f);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(bg.r, bg.g, bg.b, 0.94f);
        shapeRenderer.rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        shapeRenderer.end();

        float padding = 30f;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 1, 1, 0.2f);
        shapeRenderer.rect(padding, padding, VIRTUAL_WIDTH - padding * 2, VIRTUAL_HEIGHT - padding * 2);
        shapeRenderer.setColor(1, 1, 1, 0.15f);
        shapeRenderer.line(padding, VIRTUAL_HEIGHT - 100f, VIRTUAL_WIDTH - padding, VIRTUAL_HEIGHT - 100f);
        shapeRenderer.line(padding, 70f, VIRTUAL_WIDTH - padding, 70f);
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        fontTitle.getData().setScale(1.4f);
        fontTitle.setColor(1, 0.3f, 0.2f, 1f);
        fontTitle.draw(batch, "DEVELOPER CONSOLE", 50, VIRTUAL_HEIGHT - 45);
        fontTitle.getData().setScale(1f);
        fontTitle.setColor(1, 1, 1, 1);

        fontSmall.setColor(0.7f, 0.7f, 0.7f, 0.7f);
        fontSmall.draw(batch, Jgame.Version, VIRTUAL_WIDTH - 130, VIRTUAL_HEIGHT - 45);
        fontSmall.setColor(1, 1, 1, 1);

        fontBody.getData().setScale(0.55f);
        float lineHeight  = 24f;
        float startY      = VIRTUAL_HEIGHT - 130f;
        int   startIndex  = Math.max(0, history.size() - MAX_VISIBLE_LINES);
        int   rowIndex    = 0;
        fontBody.setColor(1, 1, 1, 0.9f);
        for (int i = startIndex; i < history.size(); i++) {
            fontBody.draw(batch, history.get(i), 50, startY - rowIndex * lineHeight);
            rowIndex++;
        }

        boolean cursorOn = ((int) (animTime * 2f)) % 2 == 0;
        String  cursor   = cursorOn ? "_" : "";
        fontBody.setColor(1, 1, 0, 1f);
        fontBody.draw(batch, "> " + inputLine.toString() + cursor, 50, 45f);
        fontBody.getData().setScale(1f);
        fontBody.setColor(1, 1, 1, 1);

        fontSmall.setColor(0.6f, 0.6f, 0.6f, 0.6f);
        fontSmall.draw(batch, "GRAVE: toggle console   ENTER: run command   TAB: autocomplete", 50, 20f);
        fontSmall.setColor(1, 1, 1, 1);

        batch.end();
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(VIRTUAL_WIDTH / 2f, VIRTUAL_HEIGHT / 2f, 0);
        camera.update();
    }

    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }
}