package me.fredthedoggy.restpapi;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class RestConfig {
    private final int port, timeoutMillis, rateLimit, rateWindowSeconds, maxConcurrent;
    private final String bind;
    private final List<String> tokens;
    private final Set<String> allowedIps;

    RestConfig(FileConfiguration yaml) {
        port = yaml.getInt("port", 8080);
        bind = yaml.getString("bind", "0.0.0.0");
        timeoutMillis = yaml.getInt("timeout-ms", 3000);
        rateLimit = yaml.getInt("rate-limit.requests", 60);
        rateWindowSeconds = yaml.getInt("rate-limit.window-seconds", 60);
        maxConcurrent = yaml.getInt("max-concurrent", 16);
        tokens = Collections.unmodifiableList(new ArrayList<>(yaml.getStringList("tokens")));
        allowedIps = Collections.unmodifiableSet(new HashSet<>(yaml.getStringList("allowed-ips")));
        if (port < 1 || port > 65535 || bind == null || bind.trim().isEmpty()
                || timeoutMillis < 100 || timeoutMillis > 30000
                || rateLimit < 1 || rateLimit > 10000 || rateWindowSeconds < 1
                || rateWindowSeconds > 3600 || maxConcurrent < 1 || maxConcurrent > 24
                || tokens.isEmpty() || tokens.stream().anyMatch(t -> t == null || t.length() < 16 || !t.equals(t.trim()))
                || new HashSet<>(tokens).size() != tokens.size()
                || allowedIps.stream().anyMatch(ip -> ip == null || ip.trim().isEmpty() || !ip.equals(ip.trim()))) {
            throw new IllegalArgumentException("Invalid REST configuration (port, bind, limits, tokens or allowed-ips)");
        }
    }

    int port() { return port; }
    String bind() { return bind; }
    int timeoutMillis() { return timeoutMillis; }
    int rateLimit() { return rateLimit; }
    int rateWindowSeconds() { return rateWindowSeconds; }
    int maxConcurrent() { return maxConcurrent; }
    List<String> tokens() { return tokens; }
    Set<String> allowedIps() { return allowedIps; }
}
