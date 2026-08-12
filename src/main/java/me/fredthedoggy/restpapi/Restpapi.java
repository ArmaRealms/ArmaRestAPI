package me.fredthedoggy.restpapi;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Restpapi extends JavaPlugin {

    private RestPapiLoader loader;
    FileConfiguration config = getConfig();
    SparkWrapper webServer;

    @Override
    public void onEnable() {
        this.loader = new RestPapiLoader(this);
        this.loader.enable();
    }

    @Override
    public void onDisable() {
        this.loader.disable();
    }

    public RestPapiLoader getLoader() {
        return loader;
    }
}
