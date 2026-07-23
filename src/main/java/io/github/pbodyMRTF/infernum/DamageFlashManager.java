package io.github.pbodyMRTF.infernum;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.Array;

public class DamageFlashManager {
    public static final int FLASH_DURATION_TICKS = 10;

    private final IdentityMap<Entity, Integer> enemyFlashStartTick = new IdentityMap<>();
    private int playerFlashStartTick = Integer.MIN_VALUE / 2;

    public void triggerEnemyFlash(Entity e, int currentTick) {
        enemyFlashStartTick.put(e, currentTick);
    }

    public boolean isEnemyFlashing(Entity e, int currentTick) {
        Integer start = enemyFlashStartTick.get(e);
        if (start == null) return false;
        return (currentTick - start) < FLASH_DURATION_TICKS;
    }

    public void triggerPlayerFlash(int currentTick) {
        playerFlashStartTick = currentTick;
    }

    public boolean isPlayerFlashing(int currentTick) {
        return (currentTick - playerFlashStartTick) < FLASH_DURATION_TICKS;
    }

    /** Call periodically (e.g. alongside entityManager.cleanup()) to avoid unbounded growth
     *  from dead/removed enemies still sitting in the map. */
    public void cleanupDead() {
        Array<Entity> toRemove = new Array<>();
        for (Entity e : enemyFlashStartTick.keys()) {
            if (e.isDead()) toRemove.add(e);
        }
        for (Entity e : toRemove) enemyFlashStartTick.remove(e);
    }
}