package io.github.pbodyMRTF.infernum.shared;

public class WeaponStats {
    public static final int SLOT_PISTOL  = 1;
    public static final int SLOT_SHOTGUN = 2;
    public static final int SLOT_SMG     = 3;

    // Bullet.BulletType enum ordinal'lerine BİREBİR eşleşiyor
    public static final byte BULLET_AMMO         = 0; // shotgun
    public static final byte BULLET_AMMO_SMG     = 1; // smg
    public static final byte BULLET_AMMO_PISTOL  = 2; // pistol

    public int fireRateTicks;
    public int bulletCount;
    public float bulletSpread;
    public float bulletSpeed;
    public boolean automatic;
    public byte bulletType;

    public static WeaponStats forSlot(int slot) {
        WeaponStats w = new WeaponStats();
        switch (slot) {
            case SLOT_PISTOL:
                w.fireRateTicks = 5;  w.bulletCount = 1; w.bulletSpread = 0f;
                w.bulletSpeed = 800f; w.automatic = false; w.bulletType = BULLET_AMMO_PISTOL;
                break;
            case SLOT_SHOTGUN:
                w.fireRateTicks = 40; w.bulletCount = 6; w.bulletSpread = 15f;
                w.bulletSpeed = 600f; w.automatic = false; w.bulletType = BULLET_AMMO;
                break;
            case SLOT_SMG:
                w.fireRateTicks = 3;  w.bulletCount = 1; w.bulletSpread = 5f;
                w.bulletSpeed = 900f; w.automatic = true; w.bulletType = BULLET_AMMO_SMG;
                break;
            default:
                w.fireRateTicks = 5; w.bulletCount = 1; w.bulletSpeed = 800f;
                w.bulletType = BULLET_AMMO_PISTOL;
        }
        return w;
    }
}