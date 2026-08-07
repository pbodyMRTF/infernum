package io.github.pbodyMRTF.infernum.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import io.github.pbodyMRTF.infernum.shared.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class GameServer {

    private static final float PLAYER_SIZE     = 64f; // player64.png varsayımı — DOĞRULAYIN
    private static final float BAYONET_RANGE   = 150f;
    private static final float PLAYER_SPEED    = 1000f;

    private static final int SHOOT_COOLDOWN_DEFAULT_TICKS   = 16;
    private static final int HIT_COOLDOWN_TICKS              = 16;
    private static final int BAYONET_COOLDOWN_TICKS          = 60;

    // WeaponStats.forSlot zaten default case ile korunuyor, ama savunma amaçlı
    // burada da açıkça sınırlıyoruz (defense-in-depth).
    private static final int MIN_WEAPON_SLOT = WeaponStats.SLOT_PISTOL;
    private static final int MAX_WEAPON_SLOT = WeaponStats.SLOT_SMG;

    // FIX: bağlantı kurulduktan sonra geçerli bir JoinMessage gelmezse
    // bağlantıyı kapatmak için join timeout (ms). Bu olmadan bir istemci
    // hiçbir şey göndermeden slotu sonsuza kadar işgal edebiliyordu.
    private static final long JOIN_TIMEOUT_MS = 5000;

    // FIX: bullets / entities listelerinin sınırsız büyümesini (bellek/CPU
    // tükenmesi, DoS) engellemek için sert üst sınırlar.
    private static final int MAX_BULLETS  = 500;
    private static final int MAX_ENTITIES = 200;

    // FIX (DoS #1 - input flood): bir client çok hızlı/çok sayıda PlayerInput
    // gönderirse, önceden pendingInputs sınırsız büyüyüp tick() içinde
    // TAMAMEN boşaltılıyordu — bu da o tick'i (dolayısıyla TÜM oyuncuları)
    // orantısız şekilde yavaşlatabiliyordu. Artık oyuncu başına sert bir
    // kuyruk üst sınırı var; sınır aşılınca fazla input sessizce düşürülür
    // (bir sonraki input zaten güncel durumu taşıyacağı için oynanışı
    // bozmaz, sadece burst'ü söndürür).
    private static final int MAX_PENDING_INPUTS_PER_PLAYER = 32;

    // FIX (DoS #2 - connection flood): JoinMessage hiç göndermeden çok
    // sayıda soket açıp bekletmek belleği/soket tablosunu tüketebilirdi.
    // Artık hem toplam "join bekleyen" bağlantı sayısına hem de aynı IP'den
    // gelen eşzamanlı bağlantı sayısına sert üst sınır konuyor.
    private static final int MAX_PENDING_CONNECTIONS = 32;
    private static final int MAX_CONNECTIONS_PER_IP  = 4;

    // FIX (#5 - kimliksiz erişim): sunucu isteğe bağlı bir parola ile
    // korunabilir ("password <parola>" konsol komutu). null ise korumasız
    // (eski davranış). Client'ların hiçbir kod/registration değişikliği
    // GEREKMEZ: parola, mevcut JoinMessage.playerName alanına
    // "parola:isim" formatında gömülerek taşınır.
    private volatile String joinPassword = null;
    private static final String PASSWORD_SEPARATOR = ":";

    private static final int MAX_PLAYER_NAME_LEN = 32;

    private Server server;
    private CollisionGrid collisionGrid;
    private float mapWidth, mapHeight;

    // DEĞİŞTİ: artık sabit boyutlu değil — `maxp` komutuyla ayarlanabiliyor.
    // Dizinin kendisi referans olarak değişebildiği (maxp ile yeniden
    // oluşturulduğu) için ARTIK bu diziyi kilit (monitor) olarak KULLANMIYORUZ;
    // onun yerine sabit/ayrı bir kilit nesnesi (playersLock) kullanıyoruz.
    private ServerPlayerState[] players = new ServerPlayerState[2];
    private final Object playersLock = new Object();
    private volatile int maxPlayers = 2;
    private static final int MIN_MAX_PLAYERS = 1;
    private static final int MAX_MAX_PLAYERS = 8;
    private volatile int connectedCount = 0;

    // FIX: bağlantı kurulmuş ama henüz geçerli JoinMessage doğrulanmamış
    // bağlantıların join-deadline'ını takip eder. Slot burada AYRILMAZ.
    private final Map<Integer, Long> pendingJoinDeadline = new HashMap<>();

    // FIX (DoS #2): aynı IP'den eşzamanlı kaç bağlantı olduğunu takip eder.
    // playersLock altında korunur, connected()/disconnected() içinde
    // güncellenir.
    private final Map<String, Integer> connectionsPerIp = new HashMap<>();

    private ServerEntityManager entityManager = new ServerEntityManager();
    private ServerSpawnManager spawnManager;
    private int score = 0;

    private List<ServerBullet> bullets = new ArrayList<>();
    private int nextBulletId = 0;

    private final java.util.Queue<PlayerInput> pendingInputs = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private int  currentTick = 0;
    private long lastTime;

    // ---- YENİ: konsol komutlarıyla yönetilen oyun durumu ----------------
    // Ağ sunucusu (Server) program açılışında hep ayakta ve bağlantı kabul
    // ediyor; ama simülasyon (tick) SADECE konsoldan `start` yazılınca
    // çalışmaya başlıyor. Artık 2. oyuncunun bağlanması tek başına oyunu
    // başlatmıyor.
    private final Object gameLock = new Object();
    private volatile boolean gameStarted = false;

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    public void start() throws IOException {
        collisionGrid = CollisionGrid.loadFromFile("collision_flape.txt");
        mapWidth  = collisionGrid.getMapWidthPixels();
        mapHeight = collisionGrid.getMapHeightPixels();
        System.out.println("Map: " + mapWidth + "x" + mapHeight);

        server = new Server(65536, 65536);
        NetworkRegistry.register(server);

        server.addListener(new Listener() {
            @Override
            public void connected(Connection c) {
                // FIX (DoS #2): bağlantı seli koruması. Önce toplam
                // "join bekleyen" (henüz slot almamış) bağlantı sayısını,
                // sonra aynı IP'den gelen eşzamanlı bağlantı sayısını
                // kontrol ediyoruz. Limit aşılırsa bağlantı hiçbir kaynak
                // ayrılmadan hemen kapatılır.
                String ip = remoteIp(c);
                synchronized (playersLock) {
                    int fromSameIp = connectionsPerIp.getOrDefault(ip, 0);
                    if (pendingJoinDeadline.size() >= MAX_PENDING_CONNECTIONS
                            || fromSameIp >= MAX_CONNECTIONS_PER_IP) {
                        System.out.println("WARNING: Connection refused (player limit reached), IP=" + ip
                                + " connID=" + c.getID());
                        c.close();
                        return;
                    }
                    connectionsPerIp.merge(ip, 1, Integer::sum);
                    // FIX: Slot artık burada ASLA verilmiyor. Bağlantı sadece
                    // "join bekliyor" olarak işaretlenir ve bir deadline konur.
                    // Böylece hiçbir şey göndermeyen bir bağlantı slot işgal edemez.
                    pendingJoinDeadline.put(c.getID(), System.currentTimeMillis() + JOIN_TIMEOUT_MS);
                }
            }

            @Override
            public void disconnected(Connection c) {
                String ip = remoteIp(c);
                synchronized (playersLock) {
                    pendingJoinDeadline.remove(c.getID());

                    // FIX (DoS #2): IP başına sayaç düşürülüyor, yoksa
                    // giden bağlantılar sonsuza dek limitte sayılmaya
                    // devam eder ve o IP kalıcı olarak engellenmiş gibi
                    // davranır.
                    Integer count = connectionsPerIp.get(ip);
                    if (count != null) {
                        if (count <= 1) connectionsPerIp.remove(ip);
                        else connectionsPerIp.put(ip, count - 1);
                    }

                    for (int i = 0; i < players.length; i++) {
                        ServerPlayerState p = players[i];
                        if (p != null && p.connectionId == c.getID()) {
                            System.out.println("Player " + p.playerId + " disconnected.");
                            players[i] = null;
                            connectedCount--;
                        }
                    }
                }
            }

            @Override
            public void received(Connection c, Object obj) {
                if (obj instanceof JoinMessage) {
                    JoinMessage jm = (JoinMessage) obj;

                    // FIX (#5 - kimliksiz erişim): parola koruması açıksa
                    // (bkz. 'password' konsol komutu), playerName alanı
                    // "parola:isim" formatında gelmek zorunda. Yeni bir
                    // mesaj sınıfı/registration GEREKTİRMEZ, mevcut
                    // JoinMessage.playerName alanı yeniden kullanılıyor.
                    String rawName = jm.playerName;
                    String effectiveName = rawName;
                    String requiredPassword = joinPassword; // volatile'ı bir kez oku (tutarlılık)
                    if (requiredPassword != null) {
                        int sep = rawName == null ? -1 : rawName.indexOf(PASSWORD_SEPARATOR);
                        if (sep < 0) {
                            System.out.println("UYARI: parola gerekli ama gönderilmedi, connID=" + c.getID() + " -> bağlantı kapatılıyor");
                            c.close();
                            return;
                        }
                        String providedPassword = rawName.substring(0, sep);
                        effectiveName = rawName.substring(sep + 1);
                        if (!constantTimeEquals(providedPassword, requiredPassword)) {
                            System.out.println("UYARI: yanlış parola, connID=" + c.getID() + " -> bağlantı kapatılıyor");
                            c.close();
                            return;
                        }
                    }

                    if (effectiveName == null || effectiveName.isEmpty()
                            || effectiveName.length() > MAX_PLAYER_NAME_LEN) {
                        System.out.println("UYARI: geçersiz JoinMessage, connID=" + c.getID() + " -> bağlantı kapatılıyor");
                        c.close();
                        return;
                    }

                    // FIX: Slot ataması artık SADECE geçerli bir JoinMessage
                    // doğrulandıktan sonra yapılıyor.
                    synchronized (playersLock) {
                        // Zaten bu bağlantı için bir oyuncu atanmışsa (duplicate
                        // JoinMessage) tekrar slot verme.
                        for (ServerPlayerState p : players) {
                            if (p != null && p.connectionId == c.getID()) return;
                        }

                        pendingJoinDeadline.remove(c.getID());

                        int pid = -1;
                        for (int i = 0; i < players.length; i++) {
                            if (players[i] == null) { pid = i; break; }
                        }
                        if (pid == -1) { c.close(); return; }

                        players[pid] = new ServerPlayerState(pid, c.getID());
                        connectedCount++;
                        System.out.println("Player " + pid + " Connected. connID=" + c.getID());

                        // NOT: gameReady artık sadece "lobi doldu" değil,
                        // "lobi doldu VE admin start/restart yazdı" anlamına
                        // gelir. Bu oyuncu bağlandığında oyun zaten
                        // çalışıyorsa (ör. maç ortasına geç katılım) direkt
                        // true gönderiyoruz; değilse false — admin 'start'
                        // yazdığında broadcastReadyToAll() ile herkese
                        // (bu oyuncu dahil) ayrı ayrı doğru assignedPlayerId
                        // ile tekrar gönderilecek.
                        JoinAckMessage ack = new JoinAckMessage();
                        ack.assignedPlayerId = pid;
                        ack.gameReady        = gameStarted;
                        server.sendToTCP(c.getID(), ack);

                        // FIX (maxp desteği): sabit "2" yerine ayarlanabilir
                        // maxPlayers kullanılıyor. Lobi tam dolduğunda,
                        // sadece son katılana değil, o an bağlı olan HERKESE
                        // güncel hazır-durumunu (oyun zaten başlamışsa true)
                        // gönderiyoruz.
                        if (connectedCount == maxPlayers) {
                            broadcastReadyToAll(gameStarted);
                        }
                    }
                    return;
                }

                if (!(obj instanceof PlayerInput)) return;
                PlayerInput input = (PlayerInput) obj;

                ServerPlayerState owner = getPlayerByConnection(c.getID());

                if (owner == null || owner.playerId != input.playerId) {
                    System.out.println("WARNING: Player ID spoofing attempt detected, connID=" + c.getID()
                            + " claimed=" + input.playerId);
                    return;
                }

                // FIX: NaN/Infinity aimAngle burada hemen temizlenir. Daha
                // önce sadece ateş anında (spawnBullets çağrılırken)
                // filtreleniyordu; bu haliyle geçersiz açı hâlâ p.lastInput'a
                // yazılıp broadcastGameState ile DİĞER oyuncuya olduğu gibi
                // gönderiliyordu (griefing / istemci tarafı NaN yayılımı riski).
                if (!Float.isFinite(input.aimAngle)) input.aimAngle = 0f;

                // FIX (DoS #1): oyuncu başına kuyruk üst sınırı. Sınır
                // aşılırsa fazla input sessizce düşürülür — kuyruk hiçbir
                // zaman MAX_PENDING_INPUTS_PER_PLAYER'ı aşamayacağı için
                // tick() içindeki drain her zaman sınırlı iş yapar.
                if (owner.queuedInputCount.get() >= MAX_PENDING_INPUTS_PER_PLAYER) {
                    return;
                }
                owner.queuedInputCount.incrementAndGet();
                pendingInputs.add(input);
            }

            private ServerPlayerState getPlayerByConnection(int connId) {
                synchronized (playersLock) {
                    for (ServerPlayerState p : players) if (p != null && p.connectionId == connId) return p;
                }
                return null;
            }
        });

        server.bind(NetworkRegistry.TCP_PORT);
        server.start();
        System.out.println("Server Started. Port: " + NetworkRegistry.TCP_PORT);

        spawnManager = new ServerSpawnManager(
                entityManager, mapWidth, mapHeight,
                new Random(), 1.2f, 0.5f
        );

        lastTime = System.nanoTime();

        // YENİ: terminal komut dinleyicisini başlat.
        startConsoleListener();
        printHelp();
        System.out.println("Oyun 'start' komutu verilene kadar BAŞLAMAYACAK (2 oyuncu bağlansa bile).");

        gameLoop();
    }

    // -----------------------------------------------------------
    // Konsol komutları
    // -----------------------------------------------------------
    private void startConsoleListener() {
        Thread consoleThread = new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    handleCommand(line.trim());
                }
            } catch (IOException e) {
                System.err.println("Konsol okuma hatası:");
                e.printStackTrace();
            }
        }, "console-listener");
        // FIX: daemon thread — JVM konsol thread'i yüzünden kapanmayı beklemesin.
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void handleCommand(String raw) {
        if (raw.isEmpty()) return;
        String[] parts = raw.trim().split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "start":
                cmdStart();
                break;
            case "stop":
                cmdStop();
                break;
            case "restart":
                cmdRestart();
                break;
            case "status":
                cmdStatus();
                break;
            case "maxp":
                cmdMaxPlayers(parts);
                break;
            case "password":
                cmdPassword(parts);
                break;
            case "help":
                printHelp();
                break;
            default:
                System.out.println("Bilinmeyen komut: '" + raw + "'. Komutlar için 'help' yazın.");
        }
    }

    private void printHelp() {
        System.out.println("--------------------------------------------------");
        System.out.println(" Commands:");
        System.out.println("   start           -> starts the game (simulation)");
        System.out.println("   stop            -> stops the game (connections remain active)");
        System.out.println("   restart         -> resets the state and restarts the game");
        System.out.println("   status          -> shows the number of connected players and game status");
        System.out.println("   maxp <n>        -> sets the maximum number of players (" + MIN_MAX_PLAYERS
                + "-" + MAX_MAX_PLAYERS + "), only when no one is connected and the game is stopped");
        System.out.println("   password <p>    -> sets a join password (client must send 'password:name')");
        System.out.println("   password off    -> disables password protection");
        System.out.println("   help            -> shows this message");
        System.out.println("--------------------------------------------------");
    }

    // FIX (#5 - kimliksiz erişim): en azından paylaşılan bir parola ile
    // temel bir erişim kontrolü sağlar. NOT: bu bağlantı içeriğini
    // ŞİFRELEMEZ (TCP hâlâ düz metin) — sadece kimliksiz/rastgele
    // bağlanmaları engeller. Gerçek uçtan uca şifreleme (TLS) için
    // kryonet'in soket katmanına SSL/TLS sarmalayıcı eklenmesi gerekir;
    // bu, client tarafında da değişiklik gerektiren ayrı bir iştir.
    private void cmdPassword(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Kullanım: password <parola>  |  password off   (mevcut: "
                    + (joinPassword == null ? "KAPALI" : "AÇIK") + ")");
            return;
        }
        String arg = parts[1];
        if (arg.equalsIgnoreCase("off")) {
            joinPassword = null;
            System.out.println("Parola koruması kapatıldı.");
            return;
        }
        if (arg.contains(PASSWORD_SEPARATOR)) {
            System.out.println("Parola ':' karakteri içeremez.");
            return;
        }
        joinPassword = arg;
        System.out.println("Parola koruması açıldı. İstemciler artık isim alanına "
                + "'parola:isim' formatında göndermeli.");
    }

    // FIX: önceden players dizisi sabit boyut 2 idi; 3. bir bağlantı
    // JoinMessage gönderdiğinde pid bulunamıyor (pid == -1) ve bağlantı
    // kapatılıyordu — bu da o istemcide beklenmedik bir "çökme/bağlanamama"
    // gibi görünüyordu. Artık maxPlayers admin tarafından ayarlanabiliyor.
    private void cmdMaxPlayers(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Kullanım: maxp <sayı>  (mevcut: " + maxPlayers + ")");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.out.println("Geçersiz sayı: '" + parts[1] + "'");
            return;
        }
        if (n < MIN_MAX_PLAYERS || n > MAX_MAX_PLAYERS) {
            System.out.println("maxp " + MIN_MAX_PLAYERS + " ile " + MAX_MAX_PLAYERS + " arasında olmalı.");
            return;
        }
        synchronized (playersLock) {
            if (connectedCount > 0 || gameStarted) {
                System.out.println("maxp can only be changed when no players are connected and the game is stopped. "
                        + "(Connected: " + connectedCount + ", game " + (gameStarted ? "running" : "stopped") + ")");
                return;
            }
            players    = new ServerPlayerState[n];
            maxPlayers = n;
        }
        System.out.println("Maximum player count set to " + n + ".");
    }

    private void cmdStart() {
        synchronized (gameLock) {
            if (gameStarted) {
                System.out.println("Game is already running.");
                return;
            }
            resetGameState();
            gameStarted = true;
            System.out.println("Game Started!. (Connected Players: " + connectedCount + "/" + maxPlayers + ")");

        }
        broadcastReadyToAll(true);
    }

    private void cmdStop() {
        synchronized (gameLock) {
            if (!gameStarted) {
                System.out.println("Game is already paused.");
                return;
            }
            gameStarted = false;
            System.out.println("Game stopped.");
        }
        // NOT: NetworkClient tarafında gameReady=false bir "lobiye dön"
        // davranışı tetiklemiyor (sadece true iken onGameReady çağrılıyor),
        // yine de ileriye dönük uyumluluk için gönderiyoruz — client
        // tarafında istenirse bu false durumunu da işleyecek bir case
        // eklenebilir.
        broadcastReadyToAll(false);
    }

    private void cmdRestart() {
        synchronized (gameLock) {
            resetGameState();
            gameStarted = true;
            System.out.println("Game Restarted.");
        }
        broadcastReadyToAll(true);
    }

    private void cmdStatus() {
        System.out.println("Connected players: " + connectedCount + "/" + maxPlayers + " | Status: "
                + (gameStarted ? "RUNNING" : "PAUSED"));
    }

    // FIX: daha önce burada yeni ve client tarafında KAYITLI OLMAYAN bir
    // mesaj sınıfı (GameControlMessage) tüm bağlı istemcilere broadcast
    // ediliyordu. Kryo (setRegistrationRequired=true) bilinmeyen class ID
    // ile karşılaşınca deserialize hatası fırlatıyor ve KryoNet bağlantıyı
    // KAPATIYORDU — "start" yazınca iki oyuncunun da anında disconnect
    // yemesinin sebebi buydu. Çözüm: yeni bir sınıfa hiç gerek yok; zaten
    // her iki tarafta da kayıtlı ve client'ın (NetworkClient) doğru şekilde
    // işlediği JoinAckMessage'ı, her oyuncuya KENDİ doğru assignedPlayerId'si
    // ile tekrar göndermek yeterli.
    private void broadcastReadyToAll(boolean ready) {
        if (server == null) return;
        synchronized (playersLock) {
            for (ServerPlayerState p : players) {
                if (p == null) continue;
                JoinAckMessage ack = new JoinAckMessage();
                ack.assignedPlayerId = p.playerId;
                ack.gameReady        = ready;
                server.sendToTCP(p.connectionId, ack);
            }
        }
    }

    // FIX: start/restart komutuyla sunucu tarafı durumu her zaman temiz bir
    // noktadan başlatılır (skor, tick sayacı, mermiler, düşmanlar, oyuncu
    // can/pozisyonları). Bağlı Connection'lar ve players[] slot atamaları
    // KORUNUR — sadece oyun içi durum sıfırlanır.
    private void resetGameState() {
        currentTick = 0;
        score = 0;
        nextBulletId = 0;
        bullets.clear();
        pendingInputs.clear();
        entityManager = new ServerEntityManager();
        spawnManager = new ServerSpawnManager(
                entityManager, mapWidth, mapHeight,
                new Random(), 1.2f, 0.5f
        );

        synchronized (playersLock) {
            for (ServerPlayerState p : players) {
                if (p == null) continue;
                p.x = 2036; p.y = 1951;
                p.hp = 3;
                p.dead = false;
                p.weaponSlot = 1;
                p.prevFireHeld = false;
                p.firedThisTick = false;
                p.firedBulletType = -1;
                p.damagedThisTick = false;
                p.bayonetUsedThisTick = false;
                p.shootCooldown.stop();
                p.hitCooldown.stop();
                p.bayonetCooldown.stop();
                p.lastInput = null;
                // FIX (DoS #1 tutarlılığı): kuyruk fiziksel olarak
                // boşaltıldığı için sayaç da sıfırlanmalı, aksi halde
                // gerçekte boş olan kuyruk için sayaç sıfır olmayan bir
                // değerde takılı kalıp bu oyuncunun gelecekteki inputlarını
                // haksız yere düşürmeye devam edebilirdi.
                p.queuedInputCount.set(0);
            }
        }
    }

    private void gameLoop() {
        while (true) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            // FIX: join-timeout süresi geçmiş ama hâlâ JoinMessage
            // göndermemiş bağlantıları kapat (bedava/sonsuz bekleme DoS'unu
            // önler). Bu bağlantılar zaten slot almadığı için players[]
            // içinde bir kaydı yok, sadece kapatılmaları yeterli.
            checkJoinTimeouts();

            // DEĞİŞTİ: artık sadece connectedCount==maxPlayers yetmiyor,
            // admin'in konsoldan 'start' yazmış olması da gerekiyor.
            if (gameStarted && connectedCount == maxPlayers) {
                synchronized (gameLock) {
                    try {
                        tick(dt);
                    } catch (Exception ex) {
                        System.err.println("Tick Error:");
                        ex.printStackTrace();
                    }
                }
            }

            long elapsed = System.nanoTime() - now;
            long sleep   = (long)(ServerTickTimer.TICK_RATE * 1_000_000_000L) - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000)); }
                catch (InterruptedException ignored) {}
            }
        }
    }

    private void checkJoinTimeouts() {
        long now = System.currentTimeMillis();
        List<Integer> expired = null;
        synchronized (playersLock) {
            for (Map.Entry<Integer, Long> e : pendingJoinDeadline.entrySet()) {
                if (now >= e.getValue()) {
                    if (expired == null) expired = new ArrayList<>();
                    expired.add(e.getKey());
                }
            }
            if (expired != null) {
                for (int connId : expired) pendingJoinDeadline.remove(connId);
            }
        }
        if (expired != null) {
            for (int connId : expired) {
                System.out.println("UYARI: connID=" + connId + " join-timeout, bağlantı kapatılıyor");
                Connection conn = findConnection(connId);
                if (conn != null) conn.close();
            }
        }
    }

    private Connection findConnection(int connId) {
        for (Connection c : server.getConnections()) {
            if (c.getID() == connId) return c;
        }
        return null;
    }

    // FIX (DoS #2): bağlantının IP'sini güvenli biçimde okur; herhangi bir
    // sebeple alınamazsa (soket kapanmış vb.) tüm bu IP'siz bağlantıları
    // aynı "unknown" kovasına toplayarak limitleme mantığını bozmaz.
    // FIX (#5): parola karşılaştırması '=='/String.equals yerine sabit
    // zamanlı yapılır; aksi halde erken çıkış yapan bir equals() ile
    // saldırgan yanıt süresini ölçerek parolayı karakter karakter tahmin
    // edebilirdi (timing attack).
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int diff = x.length ^ y.length;
        int len = Math.max(x.length, y.length);
        for (int i = 0; i < len; i++) {
            byte bx = i < x.length ? x[i] : 0;
            byte by = i < y.length ? y[i] : 0;
            diff |= bx ^ by;
        }
        return diff == 0;
    }

    private String remoteIp(Connection c) {
        try {
            java.net.InetSocketAddress addr = c.getRemoteAddressTCP();
            return addr != null && addr.getAddress() != null
                    ? addr.getAddress().getHostAddress() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void tick(float dt) {
        currentTick++;

        // FIX: senkronize olmayan players[] erişimini önlemek için anlık
        // kopya al (connected/disconnected başka thread'den senkronize
        // biçimde değiştiriyordu, tick thread'i kilitsiz okuyordu).
        ServerPlayerState[] snapshot;
        synchronized (playersLock) { snapshot = players.clone(); }

        PlayerInput in;
        while ((in = pendingInputs.poll()) != null) {
            // FIX (DoS #1): kuyruk sayacını düşür (bkz. received() içindeki
            // artırım). Oyuncu bu arada disconnect olmuşsa owner null gelir,
            // bu durumda düşürülecek bir şey yoktur, sorun değildir.
            ServerPlayerState owner = getPlayer(in.playerId);
            if (owner != null) owner.queuedInputCount.decrementAndGet();
            handleInput(in);
        }

        entityManager.cleanup();
        bullets.removeIf(b -> b.dead);

        // FIX: hiç kimse hayattaysa (veya kimse bağlı değilken) spawn
        // etmeye devam etmek, entity listesinin sınırsız büyümesine ve
        // gereksiz CPU/bellek tüketimine yol açıyordu.
        // DEĞİŞTİ (maxp desteği): artık sabit p0/p1 yerine dizideki TÜM
        // oyuncular üzerinden genel kontrol yapılıyor.
        boolean anyoneAlive = false;
        for (ServerPlayerState p : snapshot) {
            if (p != null && !p.dead) { anyoneAlive = true; break; }
        }
        if (anyoneAlive) {
            spawnManager.tick(currentTick, score);
        }

        for (ServerPlayerState p : snapshot) {
            if (p == null || p.dead) continue;
            applyMovement(p, dt);
            processCooldowns(p);
        }

        // DEĞİŞTİ (maxp desteği): önceden düşmanların hedef listesi sadece
        // p0 VE p1'in ikisi de bağlıysa hesaplanıyordu (2 oyuncuya kilitli).
        // Artık kaç oyuncu bağlıysa (1, 2, 3...) hayatta olan HERKES hedef
        // listesine giriyor.
        List<float[]> aliveTargets = new ArrayList<>();
        for (ServerPlayerState p : snapshot) {
            if (p != null && !p.dead) aliveTargets.add(new float[]{p.x, p.y});
        }
        if (!aliveTargets.isEmpty()) {
            entityManager.updateAll(dt, aliveTargets);
        }

        updateBullets(dt);
        handleBulletEnemyCollision();
        handlePlayerEnemyCollision();

        // NOT: cleanup burada YOK artık — bir sonraki tick'in başında yapılacak
        broadcastGameState(snapshot);
    }

    // -----------------------------------------------------------
    // Input
    // -----------------------------------------------------------
    private void handleInput(PlayerInput input) {
        ServerPlayerState p = getPlayer(input.playerId);
        if (p == null || p.dead) return;
        p.lastInput = input;

        if (input.weaponSlot > 0
                && input.weaponSlot >= MIN_WEAPON_SLOT
                && input.weaponSlot <= MAX_WEAPON_SLOT
                && input.weaponSlot != p.weaponSlot) {
            p.weaponSlot = input.weaponSlot;
            p.shootCooldown.stop(); // GameScreen'deki weaponJustChanged davranışı
        }

        if (input.bayonetPressed && !p.bayonetCooldown.isRunning()) {
            p.bayonetCooldown.start(currentTick);
            p.bayonetUsedThisTick = true;
            handleBayonet(p);
        }

        WeaponStats w = WeaponStats.forSlot(p.weaponSlot);
        boolean fireHeld = input.fireKeyboard || input.fireTrigger;

        boolean shouldFire;
        if (w.automatic) {
            shouldFire = fireHeld;
        } else {
            // yarı otomatik: sadece basma anında (edge detection)
            shouldFire = fireHeld && !p.prevFireHeld;
        }
        p.prevFireHeld = fireHeld;

        if (shouldFire && !p.shootCooldown.isRunning()) {
            float angle = Float.isFinite(input.aimAngle) ? input.aimAngle : 0f;
            spawnBullets(p, angle, w);
            p.shootCooldown = new ServerTickTimer(w.fireRateTicks);
            p.shootCooldown.start(currentTick);
            p.firedThisTick = true;
            p.firedBulletType = w.bulletType;
        }
    }

    private void applyMovement(ServerPlayerState p, float dt) {
        if (p.lastInput == null) return;
        PlayerInput in = p.lastInput;

        // FIX: klavye ve gamepad girdileri artık toplanmıyor (biri diğerini
        // ezer, ikisi birden eklenmez) ve çapraz hareket normalize ediliyor.
        // Önceki haliyle up+right basmak veya klavye+gamepad'i aynı anda
        // göndermek, oyuncuya PLAYER_SPEED'in üzerinde (√2x veya 2x) bir hız
        // kazandırıyordu (speed-hack).
        float dirX = 0f, dirY = 0f;
        if (in.up)    dirY += 1f;
        if (in.down)  dirY -= 1f;
        if (in.left)  dirX -= 1f;
        if (in.right) dirX += 1f;

        float gx = clamp(in.gamepadMoveX, -1f, 1f);
        float gy = clamp(in.gamepadMoveY, -1f, 1f);
        if (Math.abs(gx) > 0.2f) dirX = gx;
        if (Math.abs(gy) > 0.2f) dirY = -gy;

        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len > 1f) { dirX /= len; dirY /= len; }

        float mx = dirX * PLAYER_SPEED * dt;
        float my = dirY * PLAYER_SPEED * dt;

        float nextX = p.x + mx;
        float nextY = p.y + my;

        if (!isPlayerBlocked(nextX, p.y)) p.x = nextX;
        if (!isPlayerBlocked(p.x, nextY)) p.y = nextY;

        p.x = clamp(p.x, 0, mapWidth  - PLAYER_SIZE);
        p.y = clamp(p.y, 0, mapHeight - PLAYER_SIZE);
    }

    // Orijinal Player.isBlocked'daki 8 nokta kontrolü
    private boolean isPlayerBlocked(float x, float y) {
        float[][] points = {
                {x, y}, {x + PLAYER_SIZE, y},
                {x, y + PLAYER_SIZE}, {x + PLAYER_SIZE, y + PLAYER_SIZE},
                {x + PLAYER_SIZE / 2, y}, {x + PLAYER_SIZE / 2, y + PLAYER_SIZE},
                {x, y + PLAYER_SIZE / 2}, {x + PLAYER_SIZE, y + PLAYER_SIZE / 2}
        };
        for (float[] pt : points) {
            if (collisionGrid.isPlayerBlockedWorld(pt[0], pt[1])) return true;
        }
        return false;
    }

    private void processCooldowns(ServerPlayerState p) {
        if (p.shootCooldown.isFinished(currentTick))       p.shootCooldown.stop();
        if (p.hitCooldown.isFinished(currentTick))         p.hitCooldown.stop();
        if (p.bayonetCooldown.isFinished(currentTick))     p.bayonetCooldown.stop();
    }

    // -----------------------------------------------------------
    // Bullet
    // -----------------------------------------------------------
    private void spawnBullets(ServerPlayerState p, float angle, WeaponStats w) {
        // FIX: bullets listesinin sınırsız büyümesini önlemek için üst sınır.
        // (Ateş hızı bypass'ı fixlense de savunma derinliği için tutulmalı.)
        if (bullets.size() >= MAX_BULLETS) return;

        double rad = Math.toRadians(angle);
        for (int i = 0; i < w.bulletCount; i++) {
            if (bullets.size() >= MAX_BULLETS) break;

            float spread = w.bulletSpread > 0
                    ? (float)((Math.random() * 2 - 1) * w.bulletSpread)
                    : 0f;
            double a = rad + Math.toRadians(spread);

            ServerBullet b = new ServerBullet();
            b.id    = nextBulletId++;
            b.x     = p.x + PLAYER_SIZE / 2 - 4; // size/2 offset (Bullet.java: x - size/2)
            b.y     = p.y + PLAYER_SIZE / 2 - 4;
            b.vx    = (float)(Math.cos(a) * w.bulletSpeed);
            b.vy    = (float)(Math.sin(a) * w.bulletSpeed);
            b.type  = w.bulletType;
            bullets.add(b);
        }
    }

    private void updateBullets(float dt) {
        float size = 8f;
        for (ServerBullet b : bullets) {
            if (b.dead) continue;

            float oldX = b.x, oldY = b.y;
            b.x += b.vx * dt;
            b.y += b.vy * dt;

            if (collisionGrid.isBulletBlockedWorld(b.x, b.y)) {
                int oldTileX = (int)(oldX / (collisionGrid.tileWidth * 3f));
                int newTileX = (int)(b.x   / (collisionGrid.tileWidth * 3f));
                if (oldTileX != newTileX) { b.vx = -b.vx; b.x = oldX; }
                else                      { b.vy = -b.vy; b.y = oldY; }
                b.bounceCount++;
            }

            if (b.x <= 0 || b.x >= mapWidth - size)  { b.vx = -b.vx; b.bounceCount++; }
            if (b.y <= 0 || b.y >= mapHeight - size) { b.vy = -b.vy; b.bounceCount++; }

            if (b.bounceCount > 5) b.dead = true;
        }
    }

    // -----------------------------------------------------------
    // Collision — orijinal CollisionHandler.resolveDamage BİREBİR
    // -----------------------------------------------------------
    private void handleBulletEnemyCollision() {
        for (ServerEntity e : entityManager.getAll()) {
            if (e.dead) continue;
            for (ServerBullet b : bullets) {
                if (e.dead) break;
                if (b.dead) continue;
                float dist = dist(e.x + 32, e.y + 32, b.x + 4, b.y + 4);
                if (dist < 36f) {
                    int dmg = resolveDamage(e.type, b.type);
                    e.hp -= dmg;
                    e.hitSoundThisTick = resolveHitSound(e.type, b.type);
                    if (killsBullet(e.type, b.type)) b.dead = true;
                    if (e.hp <= 0) {
                        if (!e.dead) score++;
                        e.dead = true;
                    }
                }
            }
        }
    }

    private int resolveDamage(byte entityType, byte bulletType) {
        if (entityType == EntitySnapshot.TYPE_ENEMY) {
            if (bulletType == WeaponStats.BULLET_AMMO_SMG)    return 15;
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 3;
            if (bulletType == WeaponStats.BULLET_AMMO)        return 2;
        } else if (entityType == EntitySnapshot.TYPE_ENEMY2) {
            if (bulletType == WeaponStats.BULLET_AMMO)        return 30;
            if (bulletType == WeaponStats.BULLET_AMMO_SMG)    return 5;
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 9;
        } else if (entityType == EntitySnapshot.TYPE_ENEMY3) {
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 8;
            if (bulletType == WeaponStats.BULLET_AMMO_SMG)    return 2;
            if (bulletType == WeaponStats.BULLET_AMMO)        return 1;
        }
        return 1;
    }
    // SFX: 0=tin, 1=splat, 2=tin+pop
    private byte resolveHitSound(byte entityType, byte bulletType) {
        if (entityType == EntitySnapshot.TYPE_ENEMY) {
            if (bulletType == WeaponStats.BULLET_AMMO_SMG) return 1; // splat
            return 0; // pistol/shotgun ammo -> tin
        } else if (entityType == EntitySnapshot.TYPE_ENEMY2) {
            if (bulletType == WeaponStats.BULLET_AMMO) return 1; // shotgun -> splat
            return 2; // smg/pistol -> tin+pop
        } else if (entityType == EntitySnapshot.TYPE_ENEMY3) {
            if (bulletType == WeaponStats.BULLET_AMMO_PISTOL) return 1; // splat
            return 0; // tin
        }
        return 0;
    }

    // Orijinalde: Enemy'de sadece AMMO_SMG mermiyi öldürmüyor (deler), diğerleri dead=true
    private boolean killsBullet(byte entityType, byte bulletType) {
        if (entityType == EntitySnapshot.TYPE_ENEMY)
            return bulletType != WeaponStats.BULLET_AMMO_SMG;
        if (entityType == EntitySnapshot.TYPE_ENEMY2)
            return bulletType != WeaponStats.BULLET_AMMO; // AMMO(shotgun) deler
        if (entityType == EntitySnapshot.TYPE_ENEMY3)
            return bulletType != WeaponStats.BULLET_AMMO_PISTOL; // pistol deler
        return true;
    }

    private void handlePlayerEnemyCollision() {
        for (ServerPlayerState p : players) {
            if (p == null || p.dead || p.hitCooldown.isRunning()) continue;
            for (ServerEntity e : entityManager.getAll()) {
                if (e.dead) continue;
                float dist = dist(e.x + 32, e.y + 32, p.x + PLAYER_SIZE/2, p.y + PLAYER_SIZE/2);
                if (dist < 64f) {
                    p.hp--;
                    p.hitCooldown.start(currentTick);
                    p.damagedThisTick = true;
                    if (p.hp <= 0) p.dead = true;
                    break;
                }
            }
        }
    }

    private void handleBayonet(ServerPlayerState p) {
        int killed = 0;
        for (ServerEntity e : entityManager.getAll()) {
            if (e.dead) continue;
            if (dist(e.x + 32, e.y + 32, p.x + PLAYER_SIZE/2, p.y + PLAYER_SIZE/2) < BAYONET_RANGE) {
                e.dead = true;
                score++;
                killed++;
            }
        }
        if (killed >= 3 && p.hp < 3)      p.hp = Math.min(3, p.hp + 2);
        else if (killed == 2 && p.hp < 3) p.hp++;
    }

    // -----------------------------------------------------------
    // Broadcast
    // -----------------------------------------------------------
    private void broadcastGameState(ServerPlayerState[] snapshot) {
        GameState state = new GameState();
        state.tick  = currentTick;
        state.score = score;

        for (ServerPlayerState p : snapshot) {
            if (p == null) continue;
            PlayerSnapshot ps = new PlayerSnapshot();
            ps.playerId   = p.playerId;
            ps.x = p.x; ps.y = p.y;
            ps.hp = p.hp; ps.dead = p.dead;
            ps.weaponSlot = p.weaponSlot;
            ps.aimAngle = p.lastInput != null ? p.lastInput.aimAngle : 0f;
            ps.firedThisTick   = p.firedThisTick;
            ps.firedBulletType = p.firedBulletType;
            ps.damagedThisTick = p.damagedThisTick;
            ps.bayonetUsedThisTick      = p.bayonetUsedThisTick;
            ps.bayonetOnCooldown        = p.bayonetCooldown.isRunning();
            ps.bayonetCooldownProgress  = p.bayonetCooldown.isRunning()
                    ? p.bayonetCooldown.getProgress(currentTick) : 1f;
            state.players.add(ps);

            p.firedThisTick = false;
            p.damagedThisTick = false;
            p.bayonetUsedThisTick = false;
        }
        for (ServerEntity e : entityManager.getAll()) {
            EntitySnapshot es = new EntitySnapshot();
            es.id    = e.id;
            es.type  = e.type;
            es.x     = e.x;
            es.y     = e.y;
            es.hp    = e.hp;
            es.maxHp = e.maxHp;
            es.dead  = e.dead;
            es.hitSoundType = e.hitSoundThisTick;
            state.entities.add(es);

            e.hitSoundThisTick = -1;
        }
        for (ServerBullet b : bullets) {
            BulletSnapshot bs = new BulletSnapshot();
            bs.id = b.id; bs.x = b.x; bs.y = b.y;
            bs.bulletType = b.type; bs.dead = b.dead;
            state.bullets.add(bs);
        }
        server.sendToAllTCP(state);
    }

    // -----------------------------------------------------------
    private ServerPlayerState getPlayer(int pid) {
        for (ServerPlayerState p : players) if (p != null && p.playerId == pid) return p;
        return null;
    }
    private float dist(float x1,float y1,float x2,float y2){float dx=x2-x1,dy=y2-y1;return (float)Math.sqrt(dx*dx+dy*dy);}
    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}

    static class ServerPlayerState {
        int playerId, connectionId;
        float x = 2036, y = 1951;
        int hp = 3;
        boolean dead = false;
        int weaponSlot = 1;
        boolean bayonetUsedThisTick = false;
        boolean prevFireHeld = false;
        boolean firedThisTick = false;
        byte firedBulletType = -1;
        boolean damagedThisTick = false;
        ServerTickTimer shootCooldown        = new ServerTickTimer(SHOOT_COOLDOWN_DEFAULT_TICKS);
        ServerTickTimer hitCooldown          = new ServerTickTimer(HIT_COOLDOWN_TICKS);
        ServerTickTimer bayonetCooldown      = new ServerTickTimer(BAYONET_COOLDOWN_TICKS);
        PlayerInput lastInput = null;
        // FIX (DoS #1): bu oyuncuya ait, henüz tick() tarafından işlenmemiş
        // input sayısı. received() içinde artırılır, tick()'te her poll'da
        // azaltılır; MAX_PENDING_INPUTS_PER_PLAYER üst sınırını uygular.
        final java.util.concurrent.atomic.AtomicInteger queuedInputCount
                = new java.util.concurrent.atomic.AtomicInteger(0);

        ServerPlayerState(int pid, int cid) { this.playerId = pid; this.connectionId = cid; }
    }

    static class ServerBullet {
        int id;
        float x, y, vx, vy;
        int bounceCount = 0;
        byte type;
        boolean dead = false;
    }
}