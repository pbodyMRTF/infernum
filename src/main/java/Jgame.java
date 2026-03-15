import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.I18NBundle;

import java.util.Locale;
import java.util.Random;

public class Jgame extends Game {
    public BitmapFont font;
    public I18NBundle bundle;

    Random rnd = new Random();
    float renk1, renk2, renk3;
    public static String Version;

    @Override
    public void create() {
        renk1 = rnd.nextFloat();
        renk2 = rnd.nextFloat() * 0.5f;
        renk3 = rnd.nextFloat();

        Version = "Release 1.0";

        createFont();
        loadBundle();

        setScreen(new LoadingScreen(this));
    }

    public void loadBundle() {
        GameConfig cfg = ConfigManager.loadConfig();
        Locale locale = "en".equals(cfg.language)
                ? new Locale("en")
                : new Locale("tr", "TR");
        bundle = I18NBundle.createBundle(Gdx.files.internal("i18n/strings"), locale, "UTF-8");
    }

    private void createFont() {
        FreeTypeFontGenerator generator =
                new FreeTypeFontGenerator(Gdx.files.internal("fonts/font.otf"));

        FreeTypeFontGenerator.FreeTypeFontParameter param =
                new FreeTypeFontGenerator.FreeTypeFontParameter();

        param.size = 32;
        param.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        param.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        param.characters =
                "aâbcçdefgğhıijklmnoöprsştuüvwxyz" +
                        "AÂBCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVWXYZ" +
                        "0123456789" +
                        ".,:;!?()[]{}+-*/'\" #<>";

        font = generator.generateFont(param);
        font.getData().setScale(1);
        generator.dispose();
    }

    @Override
    public void render() {
        super.render();
    }

    @Override
    public void dispose() {
        if (font != null) font.dispose();
        Assets.dispose();
        super.dispose();
    }
}