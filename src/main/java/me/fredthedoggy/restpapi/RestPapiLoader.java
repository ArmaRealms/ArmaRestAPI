package me.fredthedoggy.restpapi;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class RestPapiLoader {
    private final Restpapi plugin;
    private SparkWrapper webServer;
    private RestConfig runningConfig;
    private boolean reloading;
    private boolean disabled;

    RestPapiLoader(Restpapi plugin) { this.plugin = plugin; }

    void enable() {
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            FileConfiguration yaml = plugin.getConfig();
            yaml.set("port", 8080);
            yaml.set("bind", "0.0.0.0");
            yaml.set("tokens", Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
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

    synchronized CompletableFuture<Boolean> reload() {
        if (disabled || reloading) return CompletableFuture.completedFuture(false);
        plugin.reloadConfig();
        final RestConfig next;
        try {
            next = new RestConfig(plugin.getConfig());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Invalid REST config; previous service remains active", exception);
            return CompletableFuture.completedFuture(false);
        }
        reloading = true;
        SparkWrapper previous = webServer;
        RestConfig previousConfig = runningConfig;
        return CompletableFuture.supplyAsync(() -> replace(previous, previousConfig, next));
    }

    private boolean replace(SparkWrapper previous, RestConfig previousConfig, RestConfig next) {
        try {
            if (previous != null) {
                previous.stop();
                previous.awaitStop(); // Never wait for Spark workers on the Bukkit tick thread.
            }
            synchronized (this) {
                if (disabled) return false;
            }
            SparkWrapper replacement = new SparkWrapper(plugin, next);
            replacement.start();
            synchronized (this) {
                if (disabled) {
                    replacement.stop();
                    return false;
                }
                webServer = replacement;
                runningConfig = next;
            }
            plugin.getLogger().info("REST listening on " + next.bind() + ":" + next.port());
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "REST reload failed; restoring previous service", exception);
            if (previousConfig != null && !isDisabled()) {
                try {
                    SparkWrapper restored = new SparkWrapper(plugin, previousConfig);
                    restored.start();
                    synchronized (this) {
                        if (disabled) restored.stop();
                        else webServer = restored;
                    }
                } catch (RuntimeException rollbackException) {
                    plugin.getLogger().log(Level.SEVERE, "REST rollback failed; disabling plugin", rollbackException);
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().disablePlugin(plugin));
                }
            }
            return false;
        } finally {
            synchronized (this) {
                reloading = false;
            }
        }
    }

    private synchronized boolean isDisabled() { return disabled; }

    void disable() {
        SparkWrapper server;
        synchronized (this) {
            disabled = true;
            server = webServer;
            webServer = null;
        }
        if (server != null) server.stop();
    }
}
