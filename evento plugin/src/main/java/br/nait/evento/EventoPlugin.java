```java
package br.nait.evento;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventoPlugin extends JavaPlugin implements CommandExecutor {

    private EventManager manager;
    private Location lobby;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new EventManager(this);

        getServer().getPluginManager().registerEvents(manager, this);

        EventoCommand command = new EventoCommand(this);

        getCommand("evento").setExecutor(command);
        getCommand("evento").setTabCompleter(command);

        getCommand("lobby").setExecutor(this);
        getCommand("setlobby").setExecutor(this);

        carregarLobby();

        getLogger().info("Evento ativado.");
    }

    public EventManager manager() {
        return manager;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cApenas jogadores podem usar esse comando."));
            return true;
        }

        if (command.getName().equalsIgnoreCase("setlobby")) {

            if (!player.hasPermission("evento.setlobby")) {
                player.sendMessage(color("&cVocê não tem permissão."));
                return true;
            }

            lobby = player.getLocation().clone();
```

package br.nait.evento;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
public final class EventoPlugin extends JavaPlugin {
 private EventManager manager;
 @Override public void onEnable(){saveDefaultConfig(); manager=new EventManager(this); getServer().getPluginManager().registerEvents(manager,this); EventoCommand c=new EventoCommand(this); getCommand("evento").setExecutor(c); getCommand("evento").setTabCompleter(c); getLogger().info("Evento ativado.");}
 public EventManager manager(){return manager;}
 public static String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
}
