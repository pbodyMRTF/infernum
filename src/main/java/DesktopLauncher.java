import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

class DesktopLauncher {
    static void  main() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.useVsync(true);

        config.setTitle("Infernum");
        config.setWindowIcon(Assets.Textures.ENEMY);


        config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        new Lwjgl3Application(new Jgame(), config);

    }
}
