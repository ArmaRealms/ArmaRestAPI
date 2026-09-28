package me.fredthedoggy.restpapi;

import org.bukkit.plugin.java.JavaPlugin;

public final class Restpapi extends JavaPlugin {
    private RestPapiLoader loader;

    @Override
    public void onEnable() {
        loader = new RestPapiLoader(this);
        loader.enable();
    }

    @Override
    public void onDisable() {
        if (loader != null) loader.disable();
    }

    public RestPapiLoader getLoader() { return loader; }
}
