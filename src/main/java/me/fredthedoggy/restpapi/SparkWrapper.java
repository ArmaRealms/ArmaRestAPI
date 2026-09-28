package me.fredthedoggy.restpapi;

import com.google.gson.Gson;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;
import spark.Request;
import spark.Response;
import spark.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import static spark.Service.ignite;

final class SparkWrapper {
    private static final Gson JSON = new Gson();
    private final Restpapi plugin;
    private final RestConfig config;
    private final Semaphore concurrent;
    private final RequestLimiter limiter;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Set<CompletableFuture<Lookup>> pending = ConcurrentHashMap.newKeySet();
    private Service http;

    SparkWrapper(Restpapi plugin, RestConfig config) {
        this.plugin = plugin;
        this.config = config;
        concurrent = new Semaphore(config.maxConcurrent());
        limiter = new RequestLimiter(config.rateLimit(), config.rateWindowSeconds());
    }

    void start() {
        Service service = ignite();
        http = service;
        AtomicReference<Exception> startFailure = new AtomicReference<>();
        boolean routesRegistered = false;
        try {
            service.initExceptionHandler(startFailure::set);
            service.untrustForwardHeaders();
            service.ipAddress(config.bind());
            service.port(config.port());
            service.threadPool(32, 4, 30000);
            service.get("/server/:placeholder", (request, response) -> handle(request, response, false));
            routesRegistered = true;
            service.get("/:uuid/:placeholder", (request, response) -> handle(request, response, true));
            service.notFound((request, response) -> json(response, 404, "Invalid URI"));
            service.internalServerError((request, response) -> json(response, 500, "Internal Server Error"));
            service.awaitInitialization();
            if (startFailure.get() != null) {
                throw new IllegalStateException("HTTP listener could not bind", startFailure.get());
            }
        } catch (RuntimeException exception) {
            if (routesRegistered) {
                stop();
                awaitStop();
            }
            else {
                accepting.set(false);
                http = null;
            }
            throw exception;
        }
    }

    private String handle(Request request, Response response, boolean playerRoute) {
        response.type("application/json");
        if (!accepting.get() || !plugin.isEnabled()) return json(response, 503, "Service Unavailable");
        if (!authorized(request.headers("Token"))) return json(response, 401, "Unauthorized");
        // Use the socket peer; forwarded headers can be forged unless a trusted proxy strips them.
        if (!config.allowedIps().isEmpty() && !config.allowedIps().contains(request.ip())) {
            return json(response, 403, "Forbidden");
        }
        if (!limiter.allow(request.ip())) return json(response, 429, "Too Many Requests");
        if (!concurrent.tryAcquire()) return json(response, 503, "Service Busy");
        CompletableFuture<Lookup> future = new CompletableFuture<>();
        try {
            String name = request.params(":placeholder");
            if (name == null || name.isEmpty() || name.length() > 256 || name.indexOf('%') >= 0) {
                return json(response, 400, "Invalid Placeholder");
            }
            UUID uuid = null;
            if (playerRoute) {
                try {
                    uuid = UUID.fromString(request.params(":uuid"));
                } catch (IllegalArgumentException exception) {
                    return json(response, 400, "Invalid UUID");
                }
            }
            final UUID playerId = uuid;
            final String expression = "%" + name + "%";
            track(future);
            BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
                if (!accepting.get() || future.isDone()) return;
                try {
                    OfflinePlayer player = playerId == null ? null : Bukkit.getOfflinePlayer(playerId);
                    if (player != null && !player.hasPlayedBefore() && !player.isOnline()) {
                        future.complete(new Lookup(400, "Player Has Not Played Before"));
                        return;
                    }
                    String result = PlaceholderAPI.setPlaceholders(player, expression);
                    future.complete(resolveResult(expression, result));
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, "Placeholder lookup failed", exception);
                    future.complete(new Lookup(500, "Internal Server Error"));
                }
            });
            try {
                Lookup result = future.get(config.timeoutMillis(), TimeUnit.MILLISECONDS);
                return json(response, Integer.parseInt(result.status), result.message);
            } catch (TimeoutException exception) {
                future.cancel(false);
                task.cancel();
                return json(response, 504, "Lookup Timed Out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                task.cancel();
                return json(response, 503, "Service Unavailable");
            } catch (ExecutionException exception) {
                plugin.getLogger().log(Level.WARNING, "Placeholder lookup failed", exception);
                return json(response, 500, "Internal Server Error");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not schedule placeholder lookup", exception);
            return json(response, 503, "Service Unavailable");
        } finally {
            pending.remove(future);
            concurrent.release();
        }
    }

    boolean authorized(String provided) {
        if (provided == null || provided.isEmpty()) return false;
        byte[] candidate = provided.getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (String token : config.tokens()) {
            matched |= MessageDigest.isEqual(candidate, token.getBytes(StandardCharsets.UTF_8));
        }
        return matched;
    }

    static int placeholderStatus(String expression, String result) {
        return expression.equals(result) ? 406 : 200;
    }

    private static Lookup resolveResult(String expression, String result) {
        int status = placeholderStatus(expression, result);
        return new Lookup(status, status == 406 ? "Invalid Placeholder" : result);
    }

    void track(CompletableFuture<Lookup> future) {
        pending.add(future);
        if (!accepting.get()) future.complete(new Lookup(503, "Service Unavailable"));
    }

    static String json(Response response, int status, String message) {
        response.status(status);
        response.type("application/json");
        return JSON.toJson(new Lookup(status, message));
    }

    void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        accepting.set(false);
        for (CompletableFuture<Lookup> future : pending) {
            future.complete(new Lookup(503, "Service Unavailable"));
        }
        if (http != null) http.stop();
    }

    void awaitStop() {
        if (http != null) http.awaitStop();
    }

    static final class Lookup {
        private final String status;
        private final String message;

        Lookup(int status, String message) {
            this.status = Integer.toString(status);
            this.message = message;
        }
    }
}
