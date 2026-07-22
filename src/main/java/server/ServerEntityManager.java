package server;

import java.util.ArrayList;
import java.util.List;

public class ServerEntityManager {
    private List<ServerEntity> entities = new ArrayList<>();

    public void add(ServerEntity e) { entities.add(e); }

    // 2 oyuncu için: her entity en yakın oyuncuya gitsin
    public void updateAll(float dt, List<float[]> aliveTargets) {
        if (aliveTargets.isEmpty()) return;
        for (ServerEntity e : entities) {
            if (e.dead) continue;
            float bestX = aliveTargets.get(0)[0];
            float bestY = aliveTargets.get(0)[1];
            float bestDist = dist(e.x, e.y, bestX, bestY);
            for (float[] t : aliveTargets) {
                float d = dist(e.x, e.y, t[0], t[1]);
                if (d < bestDist) { bestDist = d; bestX = t[0]; bestY = t[1]; }
            }
            e.update(dt, bestX, bestY);
        }
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public void cleanup() {
        entities.removeIf(e -> e.dead);
    }

    public List<ServerEntity> getAll() { return entities; }
}