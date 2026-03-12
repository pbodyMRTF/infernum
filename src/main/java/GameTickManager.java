import com.badlogic.gdx.utils.Array;

public class GameTickManager {
    private static final float TICK_RATE = 1f / 20f; // 20 TPS (Tick Per Second)

    private float tickTimer = 0;
    private int currentTick = 0;
    private Array<TickListener> listeners = new Array<>();


    public void update(float delta) {
        tickTimer += delta;

        while (tickTimer >= TICK_RATE) {
            tick();
            tickTimer -= TICK_RATE;
            currentTick++;
        }
    }


    private void tick() {
        // Tüm listener'ları bilgilendir
        for (TickListener listener : listeners) {
            listener.onTick(currentTick);
        }
    }


    public void addListener(TickListener listener) {
        listeners.add(listener);
    }


    public void removeListener(TickListener listener) {
        listeners.removeValue(listener, true);
    }


    public int getCurrentTick() {
        return currentTick;
    }


    public boolean isTickMultiple(int multiple) {
        return currentTick % multiple == 0;
    }


    public boolean hasTicksPassed(int lastTick, int ticksToWait) {
        return (currentTick - lastTick) >= ticksToWait;
    }


    public void reset() {
        tickTimer = 0;
        currentTick = 0;
    }


    public interface TickListener {
        void onTick(int currentTick);
    }


    public static class TickTimer {
        private int startTick;
        private int durationTicks;
        private boolean running;

        public TickTimer(int durationInTicks) {
            this.durationTicks = durationInTicks;
            this.running = false;
        }

        public void start(int currentTick) {
            this.startTick = currentTick;
            this.running = true;
        }

        public boolean isFinished(int currentTick) {
            return running && (currentTick - startTick) >= durationTicks;
        }

        public void stop() {
            running = false;
        }

        public boolean isRunning() {
            return running;
        }

        public int getRemainingTicks(int currentTick) {
            if (!running) return 0;
            int elapsed = currentTick - startTick;
            return Math.max(0, durationTicks - elapsed);
        }

        public float getRemainingSeconds(int currentTick) {
            return getRemainingTicks(currentTick) * TICK_RATE;
        }


        public float getProgress(int currentTick) {
            if (!running) return 1.0f;

            int elapsed = currentTick - startTick;
            if (elapsed >= durationTicks) return 1.0f;

            return (float) elapsed / (float) durationTicks;
        }
    }
}