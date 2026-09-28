package me.fredthedoggy.restpapi;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.concurrent.CompletableFuture;

public final class RestPapiCommand implements CommandExecutor {
    private final Restpapi plugin;

    RestPapiCommand(Restpapi plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            CompletableFuture<Boolean> result = plugin.getLoader().reload();
            if (result.isDone()) {
                sendResult(sender, result.getNow(false));
            } else {
                sender.sendMessage(ChatColor.YELLOW + "RestPAPI reload in progress.");
                result.whenComplete((success, error) -> {
                    if (!plugin.isEnabled()) return;
                    try {
                        Bukkit.getScheduler().runTask(plugin, () -> sendResult(sender, error == null && success));
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Could not deliver REST reload result: " + exception.getMessage());
                    }
                });
            }
        } else {
            sender.sendMessage(ChatColor.GREEN + "Usage: /" + label + " reload");
        }
        return true;
    }

    private void sendResult(CommandSender sender, boolean success) {
        sender.sendMessage((success ? ChatColor.GREEN : ChatColor.RED)
                + (success ? "RestPAPI configuration reloaded." : "RestPAPI reload failed; check console."));
    }
}
