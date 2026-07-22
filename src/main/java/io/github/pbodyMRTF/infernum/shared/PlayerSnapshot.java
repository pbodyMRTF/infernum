package io.github.pbodyMRTF.infernum.shared;

public class PlayerSnapshot {
    public int playerId;              // 0 veya 1
    public float x, y;
    public int hp;
    public boolean dead;
    public int weaponSlot;
    public float aimAngle;

    public boolean firedThisTick;
    public byte firedBulletType;

    public PlayerSnapshot() {}
}