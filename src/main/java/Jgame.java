import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.I18NBundle;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class Jgame extends Game {

    private final Map<Integer, BitmapFont> fonts = new HashMap<>();

    public static final int FONT_SIZE_8  = 8;
    public static final int FONT_SIZE_16 = 16;
    public static final int FONT_SIZE_24 = 24;
    public static final int FONT_SIZE_32 = 32;
    public static final int FONT_SIZE_48 = 48;
    public static final int FONT_SIZE_64 = 64;

    public static final int FONT_SIZE_96 = 96;

    private static final String TURKISH_CHARS =
            "aâbcçdefgğhıijklmnoöprsştuüvwxyz" +
                    "AÂBCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVWXYZ" +
                    "0123456789" +
                    ".,:;!?()[]{}+-*/'\" #<>|";

    public I18NBundle bundle;

    Random rnd = new Random();
    public static String Version;

    @Override
    public void create() {
        Version = "1.1 rc1";
        createFonts();
        loadBundle();
        setScreen(new LoadingScreen(this));
    }

    public void loadBundle() {
        GameConfig cfg = ConfigManager.loadConfig();
        Locale locale = "en".equals(cfg.language)
                ? new Locale("en")
                : new Locale("tr", "TR");
        bundle = I18NBundle.createBundle(
                Gdx.files.internal("i18n/strings"), locale, "UTF-8");
    }

    private void createFonts() {
        FreeTypeFontGenerator generator =
                new FreeTypeFontGenerator(Gdx.files.internal("fonts/font.otf"));

        int[] sizes = {
                FONT_SIZE_8, FONT_SIZE_16, FONT_SIZE_24,
                FONT_SIZE_32, FONT_SIZE_48, FONT_SIZE_64, FONT_SIZE_96
        };

        for (int size : sizes) {
            FreeTypeFontGenerator.FreeTypeFontParameter param =
                    new FreeTypeFontGenerator.FreeTypeFontParameter();

            param.size       = size;
            param.minFilter  = Texture.TextureFilter.Linear;
            param.magFilter  = Texture.TextureFilter.Linear;
            param.characters = TURKISH_CHARS;

            fonts.put(size, generator.generateFont(param));
        }

        generator.dispose();
    }

    // boyuta göre font ver
    public BitmapFont getFont(int size) {
        BitmapFont f = fonts.get(size);
        return f != null ? f : fonts.get(FONT_SIZE_32);
    }

    @Override
    public void render() {
        super.render();
    }

    @Override
    public void dispose() {
        for (BitmapFont f : fonts.values()) f.dispose();
        fonts.clear(); // BU BYURAYA OZAEL BİR ŞEY SAKIN BAŞK GABİR YEREDE AYNI ŞEYİ YAPMA NOLUR YAPMA
        super.dispose();
    }
}