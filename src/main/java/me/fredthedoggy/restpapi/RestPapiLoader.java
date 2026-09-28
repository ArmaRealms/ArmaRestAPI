package me.fredthedoggy.restpapi;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public final class RestPapiLoader {
    private final Restpapi plugin;
    private SparkWrapper webServer;
    private RestConfig runningConfig;

    RestPapiLoader(Restpapi plugin) { this.plugin = plugin; }

    void enable() {
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            FileConfiguration yaml = plugin.getConfig();
            yaml.set("port", 8080);
            yaml.set("bind", "0.0.0.0");
            yaml.set("tokens", Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
            yaml.set("timeout-ms", 3000);
            yaml.set("max-concurrent", 16);
            yaml.set("rate-limit.requests", 60);
            yaml.set("rate-limit.window-seconds", 60);
            yaml.set("allowed-ips", java.util.Collections.emptyList());
            plugin.saveConfig();
        }
        Objects.requireNonNull(plugin.getCommand("restpapi")).setExecutor(new RestPapiCommand(plugin));
        Objects.requireNonNull(plugin.getCommand("rpapi")).setExecutor(new RestPapiCommand(plugin));
        try {
            RestConfig config = new RestConfig(plugin.getConfig());
            SparkWrapper server = new SparkWrapper(plugin, config);
            server.start();
            webServer = server;
            runningConfig = config;
            plugin.getLogger().info("REST listening on " + config.bind() + ":" + config.port());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "REST could not start; disabling plugin", exception);
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    synchronized boolean reload() {
        plugin.reloadConfig();
        final RestConfig next;
        try {
            next = new RestConfig(plugin.getConfig());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Invalid REST config; previous service remains active", exception);
            return false;
        }
        SparkWrapper previous = webServer;
        if (previous != null) previous.stop();
        webServer = null;
        SparkWrapper replacement = new SparkWrapper(plugin, next);
        try {
            replacement.start();
            webServer = replacement;
            runningConfig = next;
            plugin.getLogger().info("REST listening on " + next.bind() + ":" + next.port());
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "REST reload failed; restoring previous service", exception);
            if (runningConfig != null) {
                try {
                    SparkWrapper restored = new SparkWrapper(plugin, runningConfig);
                    restored.start();
                    webServer = restored;
                } catch (RuntimeException rollbackException) {
                    plugin.getLogger().log(Level.SEVERE, "REST rollback failed; disabling plugin", rollbackException);
                    Bukkit.getPluginManager().disablePlugin(plugin);
                }
            }
            return false;
        }
    }

    synchronized void disable() {
        SparkWrapper server = webServer;
        webServer = null;
        if (server != null) server.stop();
    }
}
