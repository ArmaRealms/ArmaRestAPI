# RestPlaceholderAPI

RestPAPI exposes PlaceholderAPI values from each Bukkit/Paper backend over HTTP. Install PlaceholderAPI and this plugin on **each** backend you want to query. Velocity forwards Minecraft traffic, not these HTTP requests.

## Configuration

On first start, `plugins/RestPAPI/config.yml` is created with two random UUID tokens. Keep them private. Example:

```yaml
port: 11001
bind: 0.0.0.0
tokens:
  - "replace-with-a-long-random-secret"
timeout-ms: 3000
max-concurrent: 16
rate-limit:
  requests: 60
  window-seconds: 60
allowed-ips: []
```

Tokens must contain at least 16 characters, cannot be blank or padded with spaces, and must be unique. A missing or invalid token configuration prevents startup. To rotate tokens, temporarily include old and new tokens, run `/restpapi reload`, update clients, then remove the old token and reload again. Never print the tokens or put them in browser JavaScript.

`bind` selects the interface **inside the container** (default `0.0.0.0`). `allowed-ips` is an exact match list of socket peer IPs; an empty list permits any peer with a valid token. Behind Nginx it will normally see the proxy address, not the original client. It deliberately ignores `X-Forwarded-For`. The rate limit is per socket peer and uses a fixed window; no more than 4096 distinct peers are tracked. The concurrency limit returns HTTP 503 instead of queuing unbounded lookups. Placeholder evaluation runs on the Minecraft main thread; clients receive HTTP 504 if it takes longer than `timeout-ms`. A lookup already running on the main thread cannot be interrupted.

The command `/restpapi reload` reads the file again, validates it before stopping the old listener, and attempts to restore the old listener if binding the new one fails. A failed rollback disables the plugin. A successful reload updates both the port and token set.

## Requests

Both routes require the `Token` header and return JSON with string fields `status` and `message`:

```bash
curl -H "Token: YOUR_SECRET" "http://127.0.0.1:11001/da8a8993-adfa-4d29-99b1-9d0f62fbb78d/player_name"
curl -H "Token: YOUR_SECRET" "http://127.0.0.1:11001/server/server_online"
```

Use the placeholder name without percent signs. Unknown placeholders return 406, missing player data 400, bad UUID 400, unauthorized requests 401, blocked peers 403, unknown routes 404, excess requests 429, overload or shutdown 503, and timed out lookups 504. Empty resolved values are valid. Offline results depend on each PlaceholderAPI expansion's support for offline players.

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

Use JDK 25 and `bash gradlew test shadowJar` (Gradle 9.8.0). The plugin targets Paper API 1.21.11; the shaded JAR is written under `build/libs`.
