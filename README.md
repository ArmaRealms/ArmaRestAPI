# RestPlaceholderAPI

RestPAPI exposes PlaceholderAPI values from each Bukkit/Paper backend over HTTP. Install PlaceholderAPI and this plugin on **each** backend you want to query. Velocity forwards Minecraft traffic, not these HTTP requests.

## HTTP server

RestPAPI embeds **Jetty 12.1** directly. Spark Java is not used.

Jetty keeps its internal server work on its platform-thread pool and dispatches blocking application work through a Java virtual-thread executor. The REST handlers are declared as blocking handlers, so offline-player resolution and PlaceholderAPI evaluation can run on virtual threads without occupying the Minecraft server thread.

Routing uses Jetty's native `PathMappingsHandler` and `UriTemplatePathSpec`. Request concurrency is enforced by `QoSHandler`, and source-address allowlisting is enforced by `InetAccessHandler`.

## Configuration

On first start, `plugins/RestPAPI/config.yml` is created with two random UUID tokens. Keep them private. Example:

```yaml
port: 11001
bind: 0.0.0.0
tokens:
  - "replace-with-a-long-random-secret"
max-concurrent: 16
rate-limit:
  requests: 60
  window-seconds: 60
allowed-ips: []
```

Tokens must contain at least 16 characters, cannot be blank or padded with spaces, and must be unique. A missing or invalid token configuration prevents startup. To rotate tokens, temporarily include old and new tokens, run `/restpapi reload`, update clients, then remove the old token and reload again. Never print the tokens or put them in browser JavaScript.

`bind` selects the interface **inside the container** (default `0.0.0.0`). `allowed-ips` is enforced by Jetty against the real remote address of the connection rather than forwarded headers. An empty list permits any peer with a valid token. Values may use Jetty address patterns such as an exact address or CIDR range. Behind Nginx, RestPAPI will normally see the proxy address unless the network topology preserves the original peer address; it deliberately does not trust `X-Forwarded-For` for access control.

`max-concurrent` is enforced by Jetty's `QoSHandler`. Requests beyond the configured number are rejected immediately with HTTP 503 instead of accumulating an unbounded queue. Jetty's virtual-thread executor has its own resource guard, while `max-concurrent` remains the application-level limit for REST requests.

The fixed-window rate limit remains implemented by RestPAPI because Jetty's built-in DoS rate limiting uses per-second/leaky-bucket semantics rather than the existing `requests` plus arbitrary `window-seconds` contract. No more than 4096 distinct peers are tracked by the fixed-window limiter.

Placeholder evaluation, including `Bukkit.getOfflinePlayer(UUID)`, is never scheduled onto the Minecraft main thread. Both player and server placeholder routes execute from Jetty request handling. Third-party PlaceholderAPI expansions queried through this API must therefore support off-thread evaluation; PlaceholderAPI does not make expansion code thread-safe automatically.

There is no server-side placeholder timeout because synchronous third-party expansion code cannot be safely preempted. Configure request timeouts in the HTTP client or reverse proxy and use `max-concurrent` to bound simultaneous evaluations.

The command `/restpapi reload` reads the file again, validates it before stopping the old listener, and attempts to restore the old listener if binding the new one fails. Reload lifecycle work runs on a dedicated Java virtual-thread executor. A failed rollback disables the plugin. Requests that begin after shutdown starts receive 503. A successful reload updates the port, bind address, tokens, limits, and allowlist.

## Requests

Both routes require the `Token` header and return JSON with string fields `status` and `message`:

```bash
curl -H "Token: YOUR_SECRET" "http://127.0.0.1:11001/da8a8993-adfa-4d29-99b1-9d0f62fbb78d/player_name"
curl -H "Token: YOUR_SECRET" "http://127.0.0.1:11001/server/server_online"
```

Use the placeholder name without percent signs. Unknown placeholders return 406, missing player data 400, bad UUID 400, unauthorized requests 401, blocked peers 403, unknown routes 404, excess requests 429, and overload or shutdown 503. Empty resolved values are valid. Offline results depend on each PlaceholderAPI expansion's support for offline players and off-thread evaluation.

## Docker, Pterodactyl and external bots

Allocate a dedicated HTTP port to **each backend** in Pterodactyl, separate from its Minecraft port. Set the plugin's `port` to that allocation's container port. For example, Minecraft may use `172.18.0.1:10001` while the same backend's REST service uses `172.18.0.1:11001`. The `172.18.0.1` gateway belongs to a Docker node and is not reachable from an external bot. The bot needs a routed private connection (for example VPN) or an HTTPS reverse proxy on the node. Do not publish the token over plain public HTTP.

For a proxy on the same node, assign REST ports to a private/interface allocation reachable from the proxy. Confirm the effective Docker/host firewall rules restrict direct access. Example Nginx location within a TLS server:

```nginx
location /survival/ {
    proxy_pass http://172.18.0.1:11001/;
}
```

Then query `https://api.example.com/survival/UUID/player_name` with the `Token` header. Use separate allocations and tokens for other backends. The trailing slashes strip `/survival/` before forwarding. Restrict the proxy by the bot's IP and/or additional authentication and keep the per-backend token. Configure `allowed-ips` with the proxy's **socket peer** address if needed; when Docker publishes a port, verify which source IP reaches the container.

## Build

Use JDK 25 and `bash gradlew test shadowJar` (Gradle 9.8.0). The plugin targets Paper API 1.21.11 and embeds Jetty 12.1.13; the shaded JAR is written under `build/libs`.
