package io.github.pbodyMRTF.infernum.shared;

public class EntitySnapshot {
    public int id;
    public byte type;
    public float x, y;
    public int hp;
    public int maxHp;      // ← eklendi
    public boolean dead;

    public static final byte TYPE_ENEMY  = 0;
    public static final byte TYPE_ENEMY2 = 1;
    public static final byte TYPE_ENEMY3 = 2;

    public EntitySnapshot() {}
}