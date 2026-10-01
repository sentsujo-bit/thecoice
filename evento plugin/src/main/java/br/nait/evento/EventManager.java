package br.nait.evento;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class EventManager implements Listener {

    private final EventoPlugin plugin;
    private final Set<Player> players = new HashSet<>();
    private final Random random = new Random();

    private EventMode mode;
    private boolean running;
    private boolean accepting;
    private boolean spin;
    private Player runner;

    private int lavaHeight;
    private BukkitTask lavaTask;
    private BukkitTask compassTask;
    private BukkitTask spinTask;

    private final List<Location> barrierBlocks = new ArrayList<>();

    private final Scoreboard scoreboard;
    private Team runnerTeam;

    public EventManager(EventoPlugin plugin) {
        this.plugin = plugin;

        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        this.scoreboard = main;

        runnerTeam = main.getTeam("evento_runner");

        if (runnerTeam == null) {
            runnerTeam = main.registerNewTeam("evento_runner");
        }

        runnerTeam.setColor(ChatColor.RED);
    }

    public void start(EventMode newMode, boolean useSpin, Player admin) {
        if (running || accepting) {
            admin.sendMessage(msg("&cJá existe um evento acontecendo."));
            return;
        }

        mode = newMode;
        spin = useSpin;
        accepting = true;
        running = false;
        players.clear();
        runner = null;

        String modeName = newMode == EventMode.LAVA ? "LAVA" : "MANHUNT";

        broadcast("&b&lEVENTO");
        broadcast("&fUm evento &e" + modeName + " &ffoi aberto!");
        broadcast("&7Digite &a/evento entrar &7para participar.");
        broadcast("&7Você tem &e" + plugin.getConfig().getInt("entrada.segundos", 30)
                + " segundos&7 para entrar.");

        Bukkit.getOnlinePlayers().forEach(player ->
                player.sendTitle(
                        EventoPlugin.color("&b&lEVENTO"),
                        EventoPlugin.color("&fDigite &a/evento entrar"),
                        10, 60, 10
                )
        );

        int seconds = Math.max(5, plugin.getConfig().getInt("entrada.segundos", 30));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (accepting) {
                beginEvent();
            }
        }, seconds * 20L);
    }

    public void join(Player player) {
        if (!accepting && !running) {
            player.sendMessage(msg("&cNão existe nenhum evento aberto."));
            return;
        }

        if (players.contains(player)) {
            player.sendMessage(msg("&eVocê já está no evento."));
            return;
        }

        if (running) {
            player.sendMessage(msg("&cO evento já começou."));
            return;
        }

        players.add(player);

        String worldName;

        if (mode == EventMode.LAVA) {
            worldName = plugin.getConfig().getString("mundos.lava", "evento_lava");
        } else {
            worldName = plugin.getConfig().getString("mundos.manhunt", "evento_manhunt");
        }

        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            players.remove(player);
            player.sendMessage(msg("&cO mundo &e" + worldName + " &cnão existe."));
            player.sendMessage(msg("&7Crie/importe o mundo pelo Multiverse."));
            return;
        }

        preparePlayer(player);
        player.teleport(world.getSpawnLocation());

        player.sendMessage(msg("&aVocê entrou no evento!"));
        player.sendMessage(msg("&7Modo: &e" +
                (mode == EventMode.LAVA ? "Lava" : "Manhunt")));
    }

    public void leave(Player player) {
        if (!players.remove(player)) {
            player.sendMessage(msg("&cVocê não está no evento."));
            return;
        }

        if (runner == player) {
            removeRunner(player);

            if (mode == EventMode.MANHUNT && !players.isEmpty()) {
                chooseNewRunner();
            }
        }

        sendToLobby(player);
        player.sendMessage(msg("&eVocê saiu do evento."));
    }

    private void beginEvent() {
        accepting = false;

        if (players.isEmpty()) {
            broadcast("&cNenhum jogador entrou. Evento cancelado.");
            cleanup();
            return;
        }

        running = true;

        broadcast("&a&lEVENTO COMEÇOU!");

        if (mode == EventMode.LAVA) {
            startLava();
        } else {
            startManhunt();
        }
    }

    private void startManhunt() {
        if (players.size() < 2) {
            broadcast("&cÉ necessário pelo menos 2 jogadores para o Manhunt.");
            stop("&cEvento cancelado.");
            return;
        }

        if (spin) {
            startSpin();
        } else {
            chooseNewRunner();
        }

        startCompassTask();
    }

    private void startSpin() {
        final int totalTicks = Math.max(
                20,
                plugin.getConfig().getInt("manhunt.spin-segundos", 5) * 20
        );

        spinTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {

            int ticks = 0;
            Player current = null;

            @Override
            public void run() {
                if (!running || players.isEmpty()) {
                    cancelSpin();
                    return;
                }

                List<Player> list = new ArrayList<>(players);

                if (current != null) {
                    current.sendTitle("", "", 0, 0, 0);
                }

                current = list.get(random.nextInt(list.size()));

                for (Player p : players) {
                    p.sendTitle(
                            EventoPlugin.color("&b&lSORTEIO"),
                            EventoPlugin.color("&f" + current.getName()),
                            0, 5, 0
                    );
                }

                ticks += 5;

                if (ticks >= totalTicks) {
                    cancelSpin();
                    chooseNewRunner();
                }
            }
        }, 0L, 5L);
    }

    private void cancelSpin() {
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
    }

    private void chooseNewRunner() {
        if (players.isEmpty()) {
            stop("&cTodos saíram do evento.");
            return;
        }

        Player newRunner = players.stream()
                .filter(Player::isOnline)
                .findAny()
                .orElse(null);

        if (newRunner == null) {
            stop("&cNão há jogadores online.");
            return;
        }

        assignRunner(newRunner);
    }

    private void assignRunner(Player newRunner) {
        if (runner != null) {
            removeRunner(runner);
        }

        runner = newRunner;

        runnerTeam.addEntry(runner.getName());

        runner.setGlowing(
                plugin.getConfig().getBoolean("manhunt.glowing-runner", true)
        );

        runner.setPlayerListName(
                EventoPlugin.color("&c" + runner.getName())
        );

        runner.setDisplayName(
                EventoPlugin.color("&c" + runner.getName())
        );

        for (Player p : players) {
            if (p.equals(runner)) {
                p.sendMessage(msg("&c&lVOCÊ É O RUNNER!"));
                p.sendTitle(
                        EventoPlugin.color("&c&lRUNNER"),
                        EventoPlugin.color("&fFuja dos Hunters!"),
                        10, 60, 10
                );
            } else {
                p.sendMessage(msg("&c" + runner.getName()
                        + " &fé o novo Runner!"));

                p.sendTitle(
                        EventoPlugin.color("&b&lHUNTER"),
                        EventoPlugin.color("&fEncontre &c" + runner.getName()),
                        10, 60, 10
                );
            }
        }
    }

    private void removeRunner(Player player) {
        runnerTeam.removeEntry(player.getName());

        if (player.isOnline()) {
            player.setGlowing(false);
            player.setPlayerListName(player.getName());
            player.setDisplayName(player.getName());
        }
    }

    private void startCompassTask() {
        compassTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!running || mode != EventMode.MANHUNT || runner == null) {
                    return;
                }

                if (!runner.isOnline()) {
                    return;
                }

                Location target = runner.getLocation();

                for (Player hunter : new ArrayList<>(players)) {
                    if (!hunter.isOnline() || hunter.equals(runner)) {
                        continue;
                    }

                    if (!hunter.getWorld().equals(target.getWorld())) {
                        continue;
                    }

                    hunter.setCompassTarget(target);

                    ItemStack compass = hunter.getInventory().getItemInMainHand();

                    if (compass.getType() != Material.COMPASS) {
                        hunter.getInventory().setItem(0, new ItemStack(Material.COMPASS));
                    }
                }
            }
        }, 0L,
                Math.max(5, plugin.getConfig().getInt(
                        "manhunt.compass-update-ticks", 20
                )));
    }

    private void startLava() {
        World world = Bukkit.getWorld(
                plugin.getConfig().getString("mundos.lava", "evento_lava")
        );

        if (world == null) {
            stop("&cO mundo da Lava não existe.");
            return;
        }

        lavaHeight = plugin.getConfig().getInt("lava.altura-inicial", 64);

        buildBarrier(world);

        fillLavaLayer(world, lavaHeight);

        broadcast("&cA lava começou a subir!");

        int interval = Math.max(
                1,
                plugin.getConfig().getInt("lava.intervalo-segundos", 10)
        );

        lavaTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!running || mode != EventMode.LAVA) {
                    return;
                }

                int max = plugin.getConfig().getInt(
                        "lava.altura-maxima", 100
                );

                int amount = Math.max(
                        1,
                        plugin.getConfig().getInt(
                                "lava.blocos-por-subida", 1
                        )
                );

                if (lavaHeight < max) {
                    lavaHeight = Math.min(max, lavaHeight + amount);
                    fillLavaLayer(world, lavaHeight);

                    broadcast("&cA lava subiu para &eY=" + lavaHeight);

                    eliminatePlayersInLava();

                    if (lavaHeight >= max) {
                        broadcast("&cA lava chegou na altura máxima!");
                    }
                }

                eliminatePlayersInLava();
                checkLavaWinner();
            }
        }, interval * 20L, interval * 20L);
    }

    private void fillLavaLayer(World world, int y) {
        int radius = plugin.getConfig().getInt("lava.raio", 40);

        Location spawn = world.getSpawnLocation();

        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    world.getChunkAt(x >> 4, z >> 4);
                }

                Material current = world.getBlockAt(x, y, z).getType();

                if (current == Material.AIR || current == Material.WATER) {
                    world.getBlockAt(x, y, z).setType(Material.LAVA, false);
                }
            }
        }
    }

    private void eliminatePlayersInLava() {
        for (Player player : new ArrayList<>(players)) {
            if (!player.isOnline()) {
                continue;
            }

            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            if (player.getLocation().getBlockY() <= lavaHeight) {
                eliminate(player);
            }
        }
    }

    private void eliminate(Player player) {
        players.remove(player);

        player.setGameMode(GameMode.SPECTATOR);

        player.sendTitle(
                EventoPlugin.color("&c&lELIMINADO"),
                EventoPlugin.color("&7Você saiu do evento."),
                10, 50, 10
        );

        player.sendMessage(msg("&cVocê foi eliminado!"));

        checkLavaWinner();
    }

    private void checkLavaWinner() {
        if (!running || mode != EventMode.LAVA) {
            return;
        }

        List<Player> alive = new ArrayList<>();

        for (Player player : players) {
            if (player.isOnline()
                    && player.getGameMode() != GameMode.SPECTATOR) {
                alive.add(player);
            }
        }

        if (alive.size() == 1) {
            Player winner = alive.get(0);

            broadcast("&a&l" + winner.getName() + " VENCEU O EVENTO!");

            winner.sendTitle(
                    EventoPlugin.color("&a&lVITÓRIA!"),
                    EventoPlugin.color("&fVocê venceu!"),
                    10, 80, 20
            );

            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    stop("&aEvento finalizado!"), 100L);
        } else if (alive.isEmpty()) {
            stop("&cNão houve vencedor.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();

        if (!running || !players.contains(dead)) {
            return;
        }

        if (mode == EventMode.MANHUNT && dead.equals(runner)) {
            Player killer = dead.getKiller();

            removeRunner(dead);

            if (killer != null && players.contains(killer)) {
                runner = null;
                assignRunner(killer);

                broadcast("&c" + killer.getName()
                        + " &7matou o Runner e virou o novo Runner!");
            } else {
                players.remove(dead);

                if (players.size() <= 1) {
                    stop("&aManhunt finalizado.");
                } else {
                    chooseNewRunner();
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!players.contains(player)) {
            return;
        }

        players.remove(player);

        if (player.equals(runner)) {
            removeRunner(player);
            runner = null;

            if (mode == EventMode.MANHUNT && !players.isEmpty()) {
                chooseNewRunner();
            }
        }

        if (players.isEmpty() && (running || accepting)) {
            stop("&cTodos saíram do evento.");
        }
    }

    private void buildBarrier(World world) {
        if (!plugin.getConfig().getBoolean("lava.barreira", true)) {
            return;
        }

        int radius = plugin.getConfig().getInt("lava.raio", 40);

        Location spawn = world.getSpawnLocation();

        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;

        int minY = plugin.getConfig().getInt("lava.altura-inicial", 64);
        int maxY = plugin.getConfig().getInt("lava.altura-maxima", 100);

        Material material;

        try {
            material = Material.valueOf(
                    plugin.getConfig().getString(
                            "lava.material-barreira", "GLASS"
                    ).toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            material = Material.GLASS;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                placeBarrier(world, x, y, minZ, material);
                placeBarrier(world, x, y, maxZ, material);
            }

            for (int z = minZ; z <= maxZ; z++) {
                placeBarrier(world, minX, y, z, material);
                placeBarrier(world, maxX, y, z, material);
            }
        }
    }

    private void placeBarrier(
            World world,
            int x,
            int y,
            int z,
            Material material
    ) {
        if (world.getBlockAt(x, y, z).getType() == Material.AIR) {
            world.getBlockAt(x, y, z).setType(material, false);
            barrierBlocks.add(new Location(world, x, y, z));
        }
    }

    public void stop(String reason) {
        if (!running && !accepting) {
            return;
        }

        broadcast(reason);

        cleanup();
    }

    private void cleanup() {
        running = false;
        accepting = false;

        if (lavaTask != null) {
            lavaTask.cancel();
            lavaTask = null;
        }

        if (compassTask != null) {
            compassTask.cancel();
            compassTask = null;
        }

        cancelSpin();

        if (runner != null) {
            removeRunner(runner);
            runner = null;
        }

        for (Player player : new ArrayList<>(players)) {
            if (player.isOnline()) {
                player.setGlowing(false);
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                sendToLobby(player);
            }
        }

        players.clear();

        for (Location location : barrierBlocks) {
            if (location.getBlock().getType() == Material.GLASS) {
                location.getBlock().setType(Material.AIR, false);
            }
        }

        barrierBlocks.clear();

        mode = null;
    }

    private void preparePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.getInventory().clear();
    }

    private void sendToLobby(Player player) {
        World lobby = Bukkit.getWorld(
                plugin.getConfig().getString("mundos.lobby", "lobby")
        );

        if (lobby != null) {
            player.teleport(lobby.getSpawnLocation());
        }
    }

    public void status(org.bukkit.command.CommandSender sender) {
        if (!running && !accepting) {
            sender.sendMessage(msg("&7Nenhum evento ativo."));
            return;
        }

        String state = accepting ? "aguardando jogadores" : "em andamento";
        String modeName = mode == EventMode.LAVA ? "Lava" : "Manhunt";

        sender.sendMessage(msg("&fModo: &e" + modeName));
        sender.sendMessage(msg("&fEstado: &e" + state));
        sender.sendMessage(msg("&fJogadores: &e" + players.size()));

        if (runner != null) {
            sender.sendMessage(msg("&fRunner: &c" + runner.getName()));
        }

        if (mode == EventMode.LAVA) {
            sender.sendMessage(msg("&fAltura da lava: &c" + lavaHeight));
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(msg(message));
    }

    private String msg(String message) {
        String prefix = plugin.getConfig().getString(
                "mensagens.prefix",
                "&8[&bEvento&8] "
        );

        return EventoPlugin.color(prefix + message);
    }
}
