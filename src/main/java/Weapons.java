import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;

public class Weapons {
    public enum WeaponType {
        PISTOL,
        SHOTGUN,
        SMG,
        BAYONET
    }
    private String gunTexturePath;
    private WeaponType type;
    private Float gunScale;

    private int fireRateTicks;
    private int bulletCount;
    private float bulletSpeed;
    private float bulletSpread;
    private boolean isAutomatic;

    private void initializeWeapon() {
        switch (type) {
            case PISTOL:
                gunTexturePath = Assets.Textures.GUN;
                gunScale = 1.0f;
                fireRateTicks = 5;
                bulletCount = 1;
                bulletSpeed = 800f;
                bulletSpread = 0f;
                isAutomatic = false;
                break;

            case SHOTGUN:
                gunTexturePath = Assets.Textures.SHOTGUN;
                gunScale = 2f;
                fireRateTicks = 40;
                bulletCount = 6;
                bulletSpeed = 600f;
                bulletSpread = 15f;
                isAutomatic = false;
                break;
            case SMG:
                gunTexturePath = Assets.Textures.SMG;
                gunScale = 1.5f;
                fireRateTicks = 3;
                bulletCount = 1;
                bulletSpeed = 900f;
                bulletSpread = 5f;
                isAutomatic = true;
                break;
            case BAYONET:
                gunTexturePath = Assets.Textures.BAYONET;
                gunScale = 1.5f;
                fireRateTicks = 3;
                bulletCount = 10;
                bulletSpeed = 900f;
                bulletSpread = 5f;
                isAutomatic = true;
                break;
        }
    }

    public Weapons(WeaponType type) {
        this.type = type;
        initializeWeapon();
    }

    public WeaponType getType() {
        return type;
    }

    public int getFireRateTicks() {
        return fireRateTicks;
    }

    public float getGunScale() {
        return gunScale;
    }

    public String getGunTexturePath() {
        return gunTexturePath;
    }

    public int getBulletCount() {
        return bulletCount;
    }

    public float getBulletSpread() {
        return bulletSpread;
    }

    public boolean isAutomatic() {
        return isAutomatic;
    }
}