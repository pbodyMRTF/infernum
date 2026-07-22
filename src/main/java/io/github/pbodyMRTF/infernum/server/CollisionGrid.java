package io.github.pbodyMRTF.infernum.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CollisionGrid {
    public int tileWidth, tileHeight, gridWidth, gridHeight;
    public boolean[][] blocked; // [x][y]

    public static CollisionGrid loadFromFile(String path) throws IOException {
        CollisionGrid grid = new CollisionGrid();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String[] header = br.readLine().split(" ");
            grid.tileWidth  = Integer.parseInt(header[0]);
            grid.tileHeight = Integer.parseInt(header[1]);
            grid.gridWidth  = Integer.parseInt(header[2]);
            grid.gridHeight = Integer.parseInt(header[3]);
            grid.blocked = new boolean[grid.gridWidth][grid.gridHeight];

            for (int y = 0; y < grid.gridHeight; y++) {
                String line = br.readLine();
                for (int x = 0; x < grid.gridWidth; x++) {
                    grid.blocked[x][y] = line.charAt(x) == '1';
                }
            }
        }
        return grid;
    }

    public boolean isBlockedTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= gridWidth || ty >= gridHeight) return true;
        return blocked[tx][ty];
    }

    // Orijinal koddaki unitScale = 3f
    public boolean isBlockedWorld(float worldX, float worldY) {
        float tileW = tileWidth  * 3f;
        float tileH = tileHeight * 3f;
        int tx = (int) (worldX / tileW);
        int ty = (int) (worldY / tileH);
        return isBlockedTile(tx, ty);
    }

    public float getMapWidthPixels()  { return gridWidth  * tileWidth  * 3f; }
    public float getMapHeightPixels() { return gridHeight * tileHeight * 3f; }
}