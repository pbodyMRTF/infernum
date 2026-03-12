import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

public class ConfigManager {
    private static GameConfig config;

    public static void saveConfig(GameConfig config) {
        try {
            Json json = new Json();
            String jsonText = json.prettyPrint(config);
            FileHandle file = Gdx.files.local("config.json");
            file.writeString(jsonText, false);
            System.out.println("Config saved: " + jsonText);
        } catch (Exception e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    public static GameConfig loadConfig() {
        try {
            Json json = new Json();
            FileHandle file = Gdx.files.local("config.json");

            if (file.exists()) {
                config = json.fromJson(GameConfig.class, file);
                System.out.println("Config loaded from local");
            } else {
                FileHandle internalFile = Gdx.files.internal("config.json");
                if (internalFile.exists()) {
                    config = json.fromJson(GameConfig.class, internalFile);
                    System.out.println("Config loaded from internal");
                } else {
                    config = new GameConfig(); // Varsayılan config
                    System.out.println("Config created with defaults");
                }
            }
            return config;
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            return new GameConfig(); // Hata durumunda varsayılan config döndür
        }
    }

    public static GameConfig getConfig() {
        if (config == null) {
            config = loadConfig();
        }
        return config;
    }
}