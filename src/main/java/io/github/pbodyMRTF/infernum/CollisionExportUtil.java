package io.github.pbodyMRTF.infernum;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;

public class CollisionExportUtil {
    public static void export(TiledMapTileLayer wallLayer, TiledMapTileLayer obstacleLayer, String outputPath) {
        int gw = wallLayer.getWidth();
        int gh = wallLayer.getHeight();
        int tw = wallLayer.getTileWidth();
        int th = wallLayer.getTileHeight();

        StringBuilder sb = new StringBuilder();
        sb.append(tw).append(" ").append(th).append(" ").append(gw).append(" ").append(gh).append("\n");

        // 1. Sadece duvar — mermiler için
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                sb.append(wallLayer.getCell(x, y) != null ? '1' : '0');
            }
            sb.append("\n");
        }

        sb.append("---\n");

        // 2. Duvar + alçak engel — oyuncu için
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                boolean blocked = wallLayer.getCell(x, y) != null
                        || (obstacleLayer != null && obstacleLayer.getCell(x, y) != null);
                sb.append(blocked ? '1' : '0');
            }
            sb.append("\n");
        }

        Gdx.files.local(outputPath).writeString(sb.toString(), false);
        System.out.println("Collision grid export edildi: " + outputPath);
    }
}