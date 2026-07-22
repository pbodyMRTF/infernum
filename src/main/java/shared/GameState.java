package shared;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    public long tick;
    public int score;

    public List<PlayerSnapshot>  players  = new ArrayList<>();
    public List<EntitySnapshot>  entities = new ArrayList<>();
    public List<BulletSnapshot>  bullets  = new ArrayList<>();

    public GameState() {}
}