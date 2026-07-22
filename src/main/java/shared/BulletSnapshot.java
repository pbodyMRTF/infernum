package shared;

public class BulletSnapshot {
    public int id;
    public float x, y;
    public byte bulletType;           // Weapons.WeaponType ordinal
    public boolean dead;

    public BulletSnapshot() {}
}