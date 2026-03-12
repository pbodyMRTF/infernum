import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import org.w3c.dom.Text;

public class Assets {
    private static AssetManager manager;


    public static class Textures {
        public static final String PLAYER = "player64.png";
        public static final String GUN = "gun.png";
        public static final String SHOTGUN = "spas.png";
        public static final String SMG = "mp5.png";
        public static final String BAYONET = "bayonet.png";
        public static final String BULLET = "bullet8.png";
        public static final String BULLET_SMG = "bullet77.png";
        public static final String BULLET_PISTOL = "bullet87.png";
        public static final String ENEMY = "enemy.png";
        public static final String ENEMY2 = "enemy2.png";
        public static final String ENEMY3 = "enemy3.png";
        public static final String BLOOD = "particles/blood.png";
        public static final String TOZ = "particles/toz.png";
        public static final String HEART = "kalp.png";
        public static final String HEART_EMPTY = "kalp2.png";
        public static final String REGEN_KALP = "kalp3.png";
        public static final String HOTBAR1 = "hotbar1.png";
        public static final String HOTBAR2 = "hotbar2.png";
        public static final String HOTBAR3 = "hotbar3.png";


        public static final String MRTF = "mrtf.jpg";
    }


    public static class Sounds {
        public static final String SHOOT = "sfx/shot.mp3";
        public static final String POP = "sfx/pop.mp3";
        public static final String WOOD = "sfx/wood.mp3";
        public static final String SLICE = "sfx/bayonet.mp3";
        public static final String TIN = "sfx/tin.mp3";
        public static final String SHOTGUNSHOT = "sfx/shotgun.mp3";
        public static final String SMGSHOT = "sfx/smg.mp3";

        public static final String SPLAT = "sfx/splat.mp3";
    }

    public static class Musics {
    }

    public static void load() {
        manager = new AssetManager();

        manager.load(Textures.PLAYER, Texture.class);
        manager.load(Textures.GUN, Texture.class);
        manager.load(Textures.BULLET, Texture.class);
        manager.load(Textures.BULLET_SMG, Texture.class);
        manager.load(Textures.BULLET_PISTOL, Texture.class);
        manager.load(Textures.ENEMY, Texture.class);
        manager.load(Textures.ENEMY2, Texture.class);
        manager.load(Textures.ENEMY3, Texture.class);
        manager.load(Textures.BLOOD, Texture.class);
        manager.load(Textures.TOZ, Texture.class);
        manager.load(Textures.HEART, Texture.class);
        manager.load(Textures.HEART_EMPTY, Texture.class);
        manager.load(Textures.REGEN_KALP, Texture.class);
        manager.load(Textures.HOTBAR1, Texture.class);
        manager.load(Textures.HOTBAR2, Texture.class);
        manager.load(Textures.HOTBAR3, Texture.class);
        manager.load(Textures.SHOTGUN, Texture.class);
        manager.load(Textures.SMG, Texture.class);
        manager.load(Textures.BAYONET, Texture.class);

        manager.load(Textures.MRTF, Texture.class);


        manager.load(Sounds.SHOOT, Sound.class);
        manager.load(Sounds.POP, Sound.class);
        manager.load(Sounds.WOOD, Sound.class);
        manager.load(Sounds.SLICE, Sound.class);
        manager.load(Sounds.TIN, Sound.class);
        manager.load(Sounds.SHOTGUNSHOT, Sound.class);
        manager.load(Sounds.SMGSHOT, Sound.class);

        manager.load(Sounds.SPLAT, Sound.class);

    }
    public static boolean update() {
        return manager.update();
    }


    public static float getProgress() {
        return manager.getProgress();
    }


    public static Texture getTexture(String path) {
        return manager.get(path, Texture.class);
    }

    public static Sound getSound(String path) {
        return manager.get(path, Sound.class);
    }

    public static void dispose() {
        if (manager != null) {
            manager.dispose();
        }
    }
    public static boolean isLoaded() {
        return manager != null && manager.getProgress() >= 1.0f;
    }
}