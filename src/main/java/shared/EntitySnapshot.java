package shared;

public class EntitySnapshot {
    public int id;
    public byte type;                  // 0=Enemy, 1=Enemy2, 2=Enemy3
    public float x, y;
    public int hp;
    public boolean dead;

    public static final byte TYPE_ENEMY  = 0;
    public static final byte TYPE_ENEMY2 = 1;
    public static final byte TYPE_ENEMY3 = 2;

    public EntitySnapshot() {}
}