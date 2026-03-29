import com.badlogic.gdx.utils.Array;

public class EntityManager {
    private Array<Entity> entities = new Array<>();

    public void add(Entity e) {
        entities.add(e);
    }

    public void updateAll(float dt, float px, float py) {
        for (Entity e : entities) {
            if (!e.isDead()) e.update(dt, px, py);
        }
    }

    public void cleanup() {
        for (int i = entities.size - 1; i >= 0; i--) {
            if (entities.get(i).isDead()) entities.removeIndex(i);
        }
    }

    public Array<Entity> getAll() {
        return entities;
    }

    public void clear() {
        entities.clear();
    }
}