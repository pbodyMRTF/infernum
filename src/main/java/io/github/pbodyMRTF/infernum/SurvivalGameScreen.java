package io.github.pbodyMRTF.infernum;

/**
 * Eski GameScreen'in davranışının birebir aynısı: skor arttıkça SpawnManager
 * düşman spawn ediyor, oyuncu ölünce DeathScreen'e gidiliyor.
 * Ana menüden "Hızlı Oyun / Survival" seçildiğinde bu ekran açılmalı.
 */
public class SurvivalGameScreen extends AbstractGameScreen {

    private SpawnManager spawnManager;

    private float baseSpawnInterval;
    private float minSpawnInterval = 0.01f;

    public SurvivalGameScreen(final Jgame game) {
        super(game);
        spawnManager = new SpawnManager(entityManager, enemyTex, enemy2Tex, enemy3Tex,
                game.rnd, baseSpawnInterval, minSpawnInterval);
    }

    @Override
    protected String getMapFileName() {
        return "flape.tmx";
    }

    @Override
    protected String resolveDifficulty(GameConfig config) {
        return config.difficulty;
    }

    @Override
    protected void applyDifficultySettings() {
        // Not: super.applyDifficultySettings() shoot/hit cooldown'ları zaten ayarlıyor,
        // burada sadece survival'a özel spawn aralıklarını ekliyoruz.
        super.applyDifficultySettings();
        if ("ZOR".equals(difficulty)) {
            baseSpawnInterval = 0.95f;
            minSpawnInterval  = 0.3f;
        } else if ("Ben Erlik Han'ım".equals(difficulty)) {
            baseSpawnInterval = 0.5f;
            minSpawnInterval  = 0f;
        } else {
            baseSpawnInterval = 1.2f;
            minSpawnInterval  = 0.75f;
        }
    }

    @Override
    protected void onMapLoaded() {
        // spawnManager constructor'dan sonra oluştuğu için burada null olabilir
        // (ilk çağrı super() içinden gelir); güvenli şekilde kontrol ediyoruz.
        if (spawnManager != null) {
            spawnManager.setGroundLayer(groundLayer);
        }
    }

    @Override
    protected void onModeTick(int currentTick) {
        spawnManager.handleEnemySpawn(currentTick, score, tickManager);
    }

    @Override
    protected void onEnemyKilled(Entity e) {
        score++;
    }

    @Override
    protected void onPlayerDied() {
        game.setScreen(new DeathScreen(game, score));
    }

    @Override
    protected void checkLevelState() {
        // Survival'da "bitiş" diye bir şey yok, sadece ölüm var (onPlayerDied ile hallediliyor).
    }
}