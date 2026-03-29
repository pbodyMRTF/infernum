import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class HUD {
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;
    private Texture heartTex;
    private Texture heartEmptyTex;
    private Texture regenHeartTex;
    private Texture hotbar1;
    private Texture hotbar2;
    private Texture hotbar3;
    private Jgame game;

    private static final float UI_WIDTH  = 1024f;
    private static final float UI_HEIGHT = 768f;

    public HUD(BitmapFont font, ShapeRenderer shapeRenderer,
               Texture heartTex, Texture heartEmptyTex, Texture regenHeartTex,
               Texture hotbar1, Texture hotbar2, Texture hotbar3,
               Jgame game) {
        this.font          = font;
        this.shapeRenderer = shapeRenderer;
        this.heartTex      = heartTex;
        this.heartEmptyTex = heartEmptyTex;
        this.regenHeartTex = regenHeartTex;
        this.hotbar1       = hotbar1;
        this.hotbar2       = hotbar2;
        this.hotbar3       = hotbar3;
        this.game          = game;
    }

    public void render(SpriteBatch batch, Player player,
                       boolean isSlowed, float slowRemaining,
                       int score, GameTickManager.TickTimer bayonetCooldown,
                       int currentTick) {
        renderUI(batch, isSlowed, slowRemaining, score);
        drawHealthBars(batch, player);
        renderHotbar(batch, player);
        renderHearts(batch, player);
        renderBayonetCooldownBar(batch, bayonetCooldown, currentTick);
    }

    private void renderUI(SpriteBatch batch, boolean isSlowed, float slowRemaining, int score) {
        font.setColor(Color.WHITE);
        if (isSlowed) {
            font.getData().setScale(0.8f);
            font.draw(batch, game.bundle.format("game.ui.slowed", slowRemaining), UI_WIDTH / 2, UI_HEIGHT - 20);
            font.getData().setScale(1f);
        }
        font.getData().setScale(0.9f);
        font.draw(batch, game.bundle.format("game.ui.score", score), UI_WIDTH / 2, 38);
        font.getData().setScale(1f);
    }

    private void renderHotbar(SpriteBatch batch, Player player) {
        float hotbarWidth  = 100;
        float hotbarHeight = 260;
        float hotbarX      = UI_WIDTH + (hotbarWidth / 2);
        float hotbarY      = (UI_HEIGHT / 2) - (hotbarHeight / 2);

        Texture current = hotbar1;
        if (player != null && player.getWeapon() != null) {
            switch (player.getWeapon().getType()) {
                case PISTOL:  current = hotbar1; break;
                case SHOTGUN: current = hotbar2; break;
                case SMG:     current = hotbar3; break;
            }
        }
        batch.draw(current, hotbarX, hotbarY, hotbarWidth, hotbarHeight);
    }

    private void renderHearts(SpriteBatch batch, Player player) {
        int   maxHp    = 3;
        float heartSize = 64;
        float startX   = 20;
        float startY   = UI_HEIGHT - 58;

        for (int i = 0; i < maxHp; i++) {
            Texture heart;
            if (i < player.getHp()) {
                heart = player.isRegenHeart(i) ? regenHeartTex : heartTex;
            } else {
                heart = heartEmptyTex;
            }
            batch.draw(heart, startX + i * (heartSize + 5), startY, heartSize, heartSize);
        }
    }

    private void drawHealthBars(SpriteBatch batch, Player player) {
        batch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.end();
        batch.begin();
    }

    private void renderBayonetCooldownBar(SpriteBatch batch, GameTickManager.TickTimer bayonetCooldown, int currentTick) {
        batch.end();

        float barWidth  = 200;
        float barHeight = 20;
        float barX      = UI_WIDTH - barWidth - 20;
        float barY      = 40;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);

        if (bayonetCooldown != null) {
            float progress = bayonetCooldown.isRunning()
                    ? bayonetCooldown.getProgress(currentTick)
                    : 1.0f;

            if (progress >= 1.0f) {
                shapeRenderer.setColor(0.2f, 0.8f, 0.2f, 1.0f);
            } else {
                shapeRenderer.setColor(1.0f - (progress * 0.5f), progress * 0.8f, 0.1f, 1.0f);
            }
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight);
        }

        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 1f);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);
        shapeRenderer.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(0.5f);
        String label = game.bundle.get("game.ui.bayonet");
        if (bayonetCooldown != null && bayonetCooldown.isRunning()) {
            label += game.bundle.format("game.ui.bayonet.cooldown",
                    bayonetCooldown.getRemainingSeconds(currentTick));
        } else {
            label += game.bundle.get("game.ui.bayonet.ready");
        }
        font.draw(batch, label, barX, barY + barHeight + 18);
        font.getData().setScale(1f);
    }
}