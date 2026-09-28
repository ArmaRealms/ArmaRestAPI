package me.fredthedoggy.restpapi;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RestPapiCommand implements CommandExecutor {
    private final Restpapi plugin;

    RestPapiCommand(Restpapi plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            boolean success = plugin.getLoader().reload();
            sender.sendMessage((success ? ChatColor.GREEN : ChatColor.RED)
                    + (success ? "RestPAPI configuration reloaded." : "RestPAPI reload failed; check console."));
        } else {
            sender.sendMessage(ChatColor.GREEN + "Usage: /" + label + " reload");
        }
        return true;
    }
}
