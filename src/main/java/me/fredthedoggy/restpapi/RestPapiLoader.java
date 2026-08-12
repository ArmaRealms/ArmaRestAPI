package me.fredthedoggy.restpapi;

import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import static java.util.UUID.randomUUID;

public class RestPapiLoader {
    private final Restpapi parent;

    public RestPapiLoader(Restpapi parent) {
        this.parent = parent;
    }

    public void enable() {
        this.parent.webServer = new SparkWrapper();
        Objects.requireNonNull(this.parent.getCommand("restpapi")).setExecutor(new RestPapiCommand(this.parent));
        Objects.requireNonNull(this.parent.getCommand("rpapi")).setExecutor(new RestPapiCommand(this.parent));
        loadWebServer();
    }

    public void disable() {
        this.parent.webServer.destroy();
        Bukkit.getLogger().log(Level.SEVERE,"[RestPAPI] Shutting down plugin.");
    }

    public void loadWebServer() {
        this.parent.config.addDefault("port", 8080);
        List<String> defaultTokens = Arrays.asList(randomUUID().toString(), randomUUID().toString());
        this.parent.config.addDefault("tokens", defaultTokens);
        this.parent.config.options().copyDefaults(true);
        this.parent.saveConfig();
        this.parent.webServer.create(this.parent.config.getInt("port"), this.parent.config.getStringList("tokens"));
        Bukkit.getLogger().log(Level.INFO,"[RestPAPI] Enabled On Port " + this.parent.config.getInt("port"));
    }
}

