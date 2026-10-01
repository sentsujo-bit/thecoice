package br.nait.evento;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EventoCommand implements CommandExecutor, TabCompleter {

    private final EventoPlugin plugin;

    public EventoCommand(EventoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "criar" -> {

                if (!sender.hasPermission("evento.admin")) {
                    sender.sendMessage(EventoPlugin.color("&cSem permissão."));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(EventoPlugin.color(
                            "&cUse: /evento iniciar lava"
                    ));
                    sender.sendMessage(EventoPlugin.color(
                            "&cUse: /evento iniciar manhunt"
                    ));
                    sender.sendMessage(EventoPlugin.color(
                            "&cUse: /evento iniciar manhunt spin"
                    ));
                    return true;
                }

                EventMode mode;

                if (args[1].equalsIgnoreCase("lava")) {
                    mode = EventMode.LAVA;

                } else if (args[1].equalsIgnoreCase("manhunt")) {
                    mode = EventMode.MANHUNT;

                } else {
                    sender.sendMessage(EventoPlugin.color(
                            "&cEvento desconhecido."
                    ));
                    return true;
                }

                boolean spin =
                        args.length >= 3
                                && args[2].equalsIgnoreCase("spin");

                if (sender instanceof Player player) {
                    plugin.manager().start(mode, spin, player);
                } else {
                    sender.sendMessage(EventoPlugin.color(
                            "&cEsse comando precisa ser executado por um jogador."
                    ));
                }

                return true;
            }

            case "entrar" -> {

                if (sender instanceof Player player) {
                    plugin.manager().join(player);
                } else {
                    sender.sendMessage(EventoPlugin.color(
                            "&cApenas jogadores podem entrar."
                    ));
                }

                return true;
            }

            case "sair" -> {

                if (sender instanceof Player player) {
                    plugin.manager().leave(player);
                } else {
                    sender.sendMessage(EventoPlugin.color(
                            "&cApenas jogadores podem sair."
                    ));
                }

                return true;
            }

            case "status" -> {
                plugin.manager().status(sender);
                return true;
            }

            case "parar" -> {

                if (!sender.hasPermission("evento.admin")) {
                    sender.sendMessage(EventoPlugin.color(
                            "&cSem permissão."
                    ));
                    return true;
                }

                plugin.manager().stop(
                        "&cEvento encerrado pelo administrador."
                );

                return true;
            }

            default -> {
                help(sender);
                return true;
            }
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(EventoPlugin.color("&b&l===== EVENTO ====="));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento iniciar lava"
        ));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento iniciar manhunt"
        ));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento iniciar manhunt spin"
        ));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento entrar"
        ));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento sair"
        ));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento status"
        ));
        sender.sendMessage(EventoPlugin.color(
                "&e/evento parar"
        ));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {
            return Arrays.asList(
                    "criar",
                    "entrar",
                    "sair",
                    "status",
                    "parar"
            );
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("criar")) {

            return Arrays.asList(
                    "lava",
                    "manhunt"
            );
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("criar")
                && args[1].equalsIgnoreCase("manhunt")) {

            return Collections.singletonList("spin");
        }

        return Collections.emptyList();
    }
}
