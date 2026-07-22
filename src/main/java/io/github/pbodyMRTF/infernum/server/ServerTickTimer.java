package io.github.pbodyMRTF.infernum.server;

public class ServerTickTimer {
    public static final float TICK_RATE = 1f / 20f;

    private int startTick;
    private int durationTicks;
    private boolean running;

    public ServerTickTimer(int durationInTicks) {
        this.durationTicks = durationInTicks;
    }

    public void start(int currentTick) {
        this.startTick = currentTick;
        this.running = true;
    }
    public float getProgress(int currentTick) {
        if (!running) return 1f;
        int elapsed = currentTick - startTick;
        if (elapsed >= durationTicks) return 1f;
        return (float) elapsed / durationTicks;
    }

    public boolean isFinished(int currentTick) {
        return running && (currentTick - startTick) >= durationTicks;
    }

    public void stop() { running = false; }
    public boolean isRunning() { return running; }
}