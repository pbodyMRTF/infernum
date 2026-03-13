import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

class DesktopLauncher {
    static void  main() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.useVsync(true);

        config.setTitle("sübhanallah bune");
        // Başlangıçta config dosyasını oku
        GameConfig gameConfig = ConfigManager.loadConfig();
        if ("FULLSCREEN".equals(gameConfig.Screen)) {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            config.setWindowedMode(1024, 768);
        }
        new Lwjgl3Application(new Jgame(), config);

    }
}
