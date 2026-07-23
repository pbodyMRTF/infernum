package io.github.pbodyMRTF.infernum;
import box2dLight.ConeLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.World;

import java.util.HashMap;
import java.util.Map;

public class LightingManager {
    private World lightWorld;
    private RayHandler rayHandler;
    private static final float LIGHT_DISTANCE = 500f;
    private static final float LIGHT_CONE_DEGREE = 40f;
    private static final int   RAY_COUNT = 128;

    // Tek oyunculu eski kullanım için (Renderer.java bunu çağırıyor)
    private ConeLight playerCone;

    // Çoklu oyunculu kullanım için (OnlineGameScreen bunu çağıracak)
    private Map<Integer, ConeLight> playerCones = new HashMap<>();

    public LightingManager() {
        RayHandler.useDiffuseLight(true);
        lightWorld = new World(com.badlogic.gdx.math.Vector2.Zero, true);
        rayHandler = new RayHandler(lightWorld);
        rayHandler.setBlurNum(2);
        rayHandler.setAmbientLight(0.25f, 0.10f, 0.2f, 1f);

        playerCone = new ConeLight(
                rayHandler, RAY_COUNT,
                new Color(1f, 1f, 1f, 1f),
                LIGHT_DISTANCE,
                0, 0,
                0,
                LIGHT_CONE_DEGREE
        );
        playerCone.setSoft(true);
        playerCone.setXray(false);
    }

    // --- Eski, tek oyunculu API (Renderer.java için — dokunulmadı) ---
    public void updateConeLight(float x, float y, float directionDeg) {
        playerCone.setPosition(x, y);
        playerCone.setDirection(directionDeg);
    }

    // --- Yeni, çoklu oyunculu API (OnlineGameScreen için) ---
    public void updateConeLight(int playerId, float x, float y, float directionDeg) {
        ConeLight cone = playerCones.get(playerId);
        if (cone == null) {
            cone = new ConeLight(
                    rayHandler, RAY_COUNT,
                    new Color(1f, 1f, 1f, 1f),
                    LIGHT_DISTANCE, x, y, directionDeg, LIGHT_CONE_DEGREE
            );
            cone.setSoft(true);
            cone.setXray(false);
            playerCones.put(playerId, cone);
        }
        cone.setPosition(x, y);
        cone.setDirection(directionDeg);
    }

    public void removeConeLight(int playerId) {
        ConeLight cone = playerCones.remove(playerId);
        if (cone != null) cone.remove();
    }

    public void render(OrthographicCamera camera) {
        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();
    }

    public World getWorld() { return lightWorld; }

    public void dispose() {
        rayHandler.dispose();
        lightWorld.dispose();
    }
}