package shared;

public class PlayerInput {
    public int playerId;
    public long tick;

    // Hareket
    public boolean up, down, left, right;
    public float gamepadMoveX, gamepadMoveY;

    // Ateş
    public boolean fireKeyboard;       // sol tık veya boşluk
    public boolean fireTrigger;        // gamepad RT
    public float aimAngle;            // client hesaplar, server kullanır

    // Silah değiştirme
    public int weaponSlot;            // 0=yok, 1=pistol, 2=shotgun, 3=smg

    // Bayonet
    public boolean bayonetPressed;

    public PlayerInput() {}           // KryoNet için no-arg constructor şart
}