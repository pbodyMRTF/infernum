package io.github.pbodyMRTF.infernum;

import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.physics.box2d.*;

public class WallBodyBuilder {

    private static final float UNIT_SCALE = 3f; // Player.isBlocked() ve Renderer ile aynı

    /**
     * wallLayer içindeki dolu hücrelerden ışığı engelleyecek static body'ler üretir.
     * Not: obstacleLayer (dk3) bilerek dahil edilmiyor -- alçak engeller ışığı
     * değil sadece hareketi engellemeli. İstersen ikinci bir parametre ile eklenebilir.
     */
    public static void build(World world, TiledMapTileLayer wallLayer) {
        int gw = wallLayer.getWidth();
        int gh = wallLayer.getHeight();
        float tileW = wallLayer.getTileWidth()  * UNIT_SCALE;
        float tileH = wallLayer.getTileHeight() * UNIT_SCALE;

        // Basit satır birleştirme: yan yana dolu hücreleri tek fixture'a indirger,
        // fixture sayısını azaltır (performans için önemli, box2d-lights ray-cast
        // her fixture ile kesişim testi yapıyor).
        for (int y = 0; y < gh; y++) {
            int runStart = -1;
            for (int x = 0; x <= gw; x++) {
                boolean solid = x < gw && wallLayer.getCell(x, y) != null;

                if (solid && runStart == -1) {
                    runStart = x;
                } else if (!solid && runStart != -1) {
                    createBox(world, runStart, x, y, tileW, tileH);
                    runStart = -1;
                }
            }
        }
    }

    private static void createBox(World world, int xStart, int xEnd, int y, float tileW, float tileH) {
        float worldX = xStart * tileW;
        float worldY = y * tileH;
        float width  = (xEnd - xStart) * tileW;
        float height = tileH;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(worldX + width / 2f, worldY + height / 2f);

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2f, height / 2f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        // box2d-lights bu fixture'ları otomatik ray-cast engeli olarak kullanır,
        // ekstra bir "isSensor" ayarına gerek yok çünkü RayHandler kendi
        // ray-cast callback'ini kullanıyor, fizik simülasyonuna girmiyor.

        body.createFixture(fixtureDef);
        shape.dispose();
    }
}