package me.fredthedoggy.restpapi;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class RestPapiCommand implements CommandExecutor {

    private final Restpapi restpapi;

    public RestPapiCommand(Restpapi restpapi) {
        this.restpapi = restpapi;
    }

    // This method is called, when somebody uses our command
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equals("reload")) {
            restpapi.webServer.destroy();
            this.restpapi.getLoader().loadWebServer();
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.GREEN + "RestPAPI Config is being reloaded.");
            }
            Bukkit.getLogger().log(Level.INFO, "[RestPAPI] Reloading Config");
        } else {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.GREEN + "Run /restpapi reload to Reload RestPAPI");
            } else {
                Bukkit.getLogger().log(Level.INFO, "[RestPAPI] Run /restpapi reload to Reload RestPAPI");
            }
        }
        return true;
    }
}
