package io.github.pbodyMRTF.infernum.shared;
public class PlayerSnapshot {
    public int playerId;
    public float x, y;
    public int hp;
    public boolean dead;
    public int weaponSlot;
    public float aimAngle;
    public boolean firedThisTick;
    public byte firedBulletType;
    public boolean damagedThisTick;
    public PlayerSnapshot() {}
}