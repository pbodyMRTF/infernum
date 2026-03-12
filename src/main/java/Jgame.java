import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.util.Random;

public class Jgame extends Game {
    public BitmapFont font;
    // Oyun ayarları
    Random rnd = new Random();
    float renk1, renk2, renk3;
    public static String Version;


    @Override
    public void create() {
        // Rastgele arkaplan rengi
        renk1 = rnd.nextFloat();
        renk2 = rnd.nextFloat() * 0.5f;
        renk3 = rnd.nextFloat();

        Version = "Release 1.0";

        // Font oluştur
        createFont();

        // Loading screen'e başla
        setScreen(new LoadingScreen(this));
    }
    private void createFont() {
        FreeTypeFontGenerator generator =
                new FreeTypeFontGenerator(Gdx.files.internal("fonts/font.otf"));

        FreeTypeFontGenerator.FreeTypeFontParameter param =
                new FreeTypeFontGenerator.FreeTypeFontParameter();

        param.size = 32;

        // Anti-aliasing
        param.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        param.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;

        // Türkçe karakter desteği
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
        Assets.dispose(); // Tüm assetleri temizle
        super.dispose();
    }
}