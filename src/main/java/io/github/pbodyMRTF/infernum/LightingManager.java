package io.github.pbodyMRTF.infernum;

import box2dLight.ConeLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.World;

public class LightingManager {
    private World lightWorld;
    private RayHandler rayHandler;
    private ConeLight playerCone;

    private static final float LIGHT_DISTANCE = 500f;
    private static final float LIGHT_CONE_DEGREE = 40f;
    private static final int   RAY_COUNT = 128;

    public LightingManager() {
        RayHandler.useDiffuseLight(true);
        lightWorld = new World(com.badlogic.gdx.math.Vector2.Zero, true);
        rayHandler = new RayHandler(lightWorld);
        rayHandler.setBlurNum(2);
        rayHandler.setAmbientLight(0.25f, 0.10f, 0.2f, 1f); // genel karanlık ton

        playerCone = new ConeLight(
                rayHandler, RAY_COUNT,
                new Color(1f, 1f, 1f, 1f), // sıcak ışık rengi
                LIGHT_DISTANCE,
                0, 0,          // başlangıç pozisyonu, sonra update'te set edilecek
                0,             // yön açısı
                LIGHT_CONE_DEGREE
        );
        playerCone.setSoft(true);
        playerCone.setXray(false); // duvarları gerçekten engelle
    }

    public void updateConeLight(float x, float y, float directionDeg) {
        playerCone.setPosition(x, y);
        playerCone.setDirection(directionDeg);
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