package io.github.pbodyMRTF.infernum.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CollisionGrid {
    public int tileWidth, tileHeight, gridWidth, gridHeight;
    public boolean[][] wallOnly;   // sadece duvar — mermi çarpışması
    public boolean[][] full;       // duvar + alçak engel — oyuncu çarpışması

    public static CollisionGrid loadFromFile(String path) throws IOException {
        CollisionGrid grid = new CollisionGrid();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String[] header = br.readLine().split(" ");
            grid.tileWidth  = Integer.parseInt(header[0]);
            grid.tileHeight = Integer.parseInt(header[1]);
            grid.gridWidth  = Integer.parseInt(header[2]);
            grid.gridHeight = Integer.parseInt(header[3]);
            grid.wallOnly = new boolean[grid.gridWidth][grid.gridHeight];
            grid.full     = new boolean[grid.gridWidth][grid.gridHeight];

            for (int y = 0; y < grid.gridHeight; y++) {
                String line = br.readLine();
                for (int x = 0; x < grid.gridWidth; x++) grid.wallOnly[x][y] = line.charAt(x) == '1';
            }

            br.readLine(); // "---" ayırıcı

            for (int y = 0; y < grid.gridHeight; y++) {
                String line = br.readLine();
                for (int x = 0; x < grid.gridWidth; x++) grid.full[x][y] = line.charAt(x) == '1';
            }
        }
        return grid;
    }

    private boolean isBlockedTile(boolean[][] arr, int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= gridWidth || ty >= gridHeight) return true;
        return arr[tx][ty];
    }

    public boolean isPlayerBlockedWorld(float worldX, float worldY) {
        int tx = (int) (worldX / (tileWidth  * 3f));
        int ty = (int) (worldY / (tileHeight * 3f));
        return isBlockedTile(full, tx, ty);
    }

    public boolean isBulletBlockedWorld(float worldX, float worldY) {
        int tx = (int) (worldX / (tileWidth  * 3f));
        int ty = (int) (worldY / (tileHeight * 3f));
        return isBlockedTile(wallOnly, tx, ty);
    }

    public float getMapWidthPixels()  { return gridWidth  * tileWidth  * 3f; }
    public float getMapHeightPixels() { return gridHeight * tileHeight * 3f; }
}