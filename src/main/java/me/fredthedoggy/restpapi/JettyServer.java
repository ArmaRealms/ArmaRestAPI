package me.fredthedoggy.restpapi;

import com.google.gson.Gson;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.eclipse.jetty.server.handler.QoSHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class JettyServer {
    private static final Gson JSON = new Gson();
    private static final UriTemplatePathSpec SERVER_ROUTE = new UriTemplatePathSpec("/server/{placeholder}");
    private static final UriTemplatePathSpec PLAYER_ROUTE = new UriTemplatePathSpec("/{uuid}/{placeholder}");

    private final Restpapi plugin;
    private final RestConfig config;
    private final RequestLimiter limiter;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private Server server;

    JettyServer(Restpapi plugin, RestConfig config) {
        this.plugin = plugin;
        this.config = config;
        limiter = new RequestLimiter(config.rateLimit(), config.rateWindowSeconds());
    }

    void start() {
        try {
            VirtualThreadPool virtualThreads = new VirtualThreadPool();
            virtualThreads.setName("restpapi-virtual");

            QueuedThreadPool platformThreads = new QueuedThreadPool();
            platformThreads.setName("restpapi-jetty");
            platformThreads.setVirtualThreadsExecutor(virtualThreads);

            Server jetty = new Server(platformThreads);
            jetty.addBean(virtualThreads);

            ServerConnector connector = new ServerConnector(jetty);
            connector.setHost(config.bind());
            connector.setPort(config.port());
            jetty.addConnector(connector);

            PathMappingsHandler routes = new PathMappingsHandler();
            routes.addMapping(SERVER_ROUTE, new PlaceholderHandler(SERVER_ROUTE, false));
            routes.addMapping(PLAYER_ROUTE, new PlaceholderHandler(PLAYER_ROUTE, true));

            Handler routed = new Handler.Sequence(routes, new NotFoundHandler());

            JsonQoSHandler qos = new JsonQoSHandler(routed);
            qos.setMaxRequestCount(config.maxConcurrent());
            qos.setMaxSuspendedRequestCount(0);
            qos.setRejectStatusCode(HttpStatus.SERVICE_UNAVAILABLE_503);

            Handler root = qos;
            if (!config.allowedIps().isEmpty()) {
                JsonInetAccessHandler access = new JsonInetAccessHandler(root);
                access.include(config.allowedIps().toArray(String[]::new));
                root = access;
            }

            jetty.setHandler(root);
            jetty.start();
            server = jetty;
        } catch (Exception exception) {
            accepting.set(false);
            throw new IllegalStateException("Jetty HTTP server could not start", exception);
        }
    }

    private final class PlaceholderHandler extends Handler.Abstract {
        private final UriTemplatePathSpec route;
        private final boolean playerRoute;

        private PlaceholderHandler(UriTemplatePathSpec route, boolean playerRoute) {
            this.route = route;
            this.playerRoute = playerRoute;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"GET".equals(request.getMethod())) {
                return writeJson(response, callback, 404, "Invalid URI");
            }
            if (!accepting.get() || !plugin.isEnabled()) {
                return writeJson(response, callback, 503, "Service Unavailable");
            }
            if (!authorized(request.getHeaders().get("Token"))) {
                return writeJson(response, callback, 401, "Unauthorized");
            }

            String peer = Request.getRemoteAddr(request);
            if (!limiter.allow(peer)) {
                return writeJson(response, callback, 429, "Too Many Requests");
            }

            try {
                Map<String, String> params = route.getPathParams(Request.getPathInContext(request));
                String name = params.get("placeholder");
                if (name == null || name.isEmpty() || name.length() > 256 || name.indexOf('%') >= 0) {
                    return writeJson(response, callback, 400, "Invalid Placeholder");
                }

                OfflinePlayer player = null;
                if (playerRoute) {
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(params.get("uuid"));
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        return writeJson(response, callback, 400, "Invalid UUID");
                    }

                    player = Bukkit.getOfflinePlayer(playerId);
                    if (!player.hasPlayedBefore() && !player.isOnline()) {
                        return writeJson(response, callback, 400, "Player Has Not Played Before");
                    }
                }

                String expression = "%" + name + "%";
                String result = PlaceholderAPI.setPlaceholders(player, expression);
                if (!accepting.get() || !plugin.isEnabled()) {
                    return writeJson(response, callback, 503, "Service Unavailable");
                }

                int status = placeholderStatus(expression, result);
                return writeJson(response, callback, status, status == 406 ? "Invalid Placeholder" : result);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Placeholder lookup failed", exception);
                return writeJson(response, callback, 500, "Internal Server Error");
            }
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

    static String jsonPayload(int status, String message) {
        return JSON.toJson(new Lookup(status, message));
    }

    private static boolean writeJson(Response response, Callback callback, int status, String message) {
        response.setStatus(status);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json; charset=UTF-8");
        Content.Sink.write(response, true, jsonPayload(status, message), callback);
        return true;
    }

    void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        accepting.set(false);
        Server current = server;
        server = null;
        if (current == null) return;

        try {
            current.stop();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Jetty HTTP server did not stop cleanly", exception);
        }
    }

    private static final class JsonQoSHandler extends QoSHandler {
        private JsonQoSHandler(Handler handler) {
            super(handler);
        }

        @Override
        protected void reject(Request request, Response response, Callback callback, int status) {
            writeJson(response, callback, status, "Service Busy");
        }
    }

    private static final class JsonInetAccessHandler extends InetAccessHandler {
        private JsonInetAccessHandler(Handler handler) {
            super(handler);
        }

        @Override
        protected boolean onConditionsNotMet(Request request, Response response, Callback callback) {
            return writeJson(response, callback, 403, "Forbidden");
        }
    }

    private static final class NotFoundHandler extends Handler.Abstract.NonBlocking {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            return writeJson(response, callback, 404, "Invalid URI");
        }
    }

    private record Lookup(String status, String message) {
        private Lookup(int status, String message) {
            this(Integer.toString(status), message);
        }
    }
}
