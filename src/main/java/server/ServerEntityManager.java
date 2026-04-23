package server;

import java.util.ArrayList;
import java.util.List;

public class ServerEntityManager {
    private List<ServerEntity> entities = new ArrayList<>();

    public void add(ServerEntity e) { entities.add(e); }

    public void updateAll(float dt, float targetX, float targetY) {
        // 2 oyuncuda en yakın oyuncuya yürüsün
        for (ServerEntity e : entities) e.update(dt, targetX, targetY);
    }

    // 2 oyuncu için: her entity en yakın oyuncuya gitsin
    public void updateAll(float dt, float p1x, float p1y, float p2x, float p2y) {
        for (ServerEntity e : entities) {
            if (e.dead) continue;
            float d1 = dist(e.x, e.y, p1x, p1y);
            float d2 = dist(e.x, e.y, p2x, p2y);
            if (d1 <= d2) e.update(dt, p1x, p1y);
            else          e.update(dt, p2x, p2y);
        }
    }

    public void cleanup() {
        entities.removeIf(e -> e.dead);
    }

    public List<ServerEntity> getAll() { return entities; }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}