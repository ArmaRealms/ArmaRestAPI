package me.fredthedoggy.restpapi;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import spark.Service;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static spark.Service.ignite;

class SparkWrapper {
    Service http;

    void create(int port, List<String> tokens) {
        http = ignite().port(port);

        // New endpoint for server-wide placeholders
        http.get("/server/:placeholder", (request, response) -> {
            response.type("application/json");

            String placeholderResult = PlaceholderAPI.setPlaceholders(null, "%" + request.params(":placeholder") + "%");

            if (placeholderResult.equals("%" + request.params(":placeholder") + "%")) {
                response.status(406);
                return "{\"status\":\"406\",\"message\":\"Invalid Placeholder\"}";
            } else {
                response.status(200);
                return "{\"status\":\"200\",\"message\":\"" + placeholderResult + "\"}";
            }
        });

        http.get("/:uuid/:placeholder", (request, response) -> {

            Bukkit.getLogger().log(Level.SEVERE, " !!! Using insecure version TOKEN DISABLED! !!!");
            Bukkit.getLogger().log(Level.SEVERE, "Token received: " + request.headers("token"));
            /*
            // todo: add back later
            // Avoid someone spamming PlaceholderAPI requests.

            if (request.headers("token") == null) {
                response.type("application/json");
                response.status(401);
                return "{\"status\":\"401\",\"message\":\"Unauthorized\"}";
            } else if (tokens.stream().noneMatch(request.headers("token")::contains)) {
                response.type("application/json");
                response.status(401);
                return "{\"status\":\"401\",\"message\":\"Unauthorized\"}";
            } else {
             */
            response.type("application/json");
            UUID specifiedUUID;
            try {
                specifiedUUID = UUID.fromString(request.params(":uuid"));
            }
            catch(Exception e) {
                response.type("application/json");
                response.status(400);
                return "{\"status\":\"400\",\"message\":\"Invalid UUID\"}";
            }
            if (Bukkit.getOfflinePlayer(specifiedUUID).hasPlayedBefore()) {

                response.type("application/json");
                response.status(200);

                String placeholderResult =  PlaceholderAPI.setPlaceholders(
                        Bukkit.getOfflinePlayer(UUID.fromString(request.params(":uuid"))),
                        "%" + request.params(":placeholder") + "%"
                );

                String placeholder = "{\"status\":\"200\",\"message\":\"" + placeholderResult + "\"}";

                if (placeholderResult.equals("%" + request.params(":placeholder") + "%")) {

                    response.type("application/json");
                    response.status(406);
                    return "{\"status\":\"406\",\"message\":\"Invalid Placeholder\"}";

                } else {

                    return placeholder;

                }
            } else {

                response.type("application/json");
                response.status(400);
                return "{\"status\":\"400\",\"message\":\"Player Has Not Played Before\"}";

            }
            //}

        });
        http.get("/*", (request, response) -> {

            response.status(404);
            response.type("application/json");
            return "{\"status\":\"404\",\"message\":\"Invalid URI\"}";

        });
        http.get("/*/*/*", (request, response) -> {

            response.status(404);
            response.type("application/json");
            return "{\"status\":\"404\",\"message\":\"Invalid URI\"}";

        });
        http.get("/*/*/*/*", (request, response) -> {

            response.status(404);
            response.type("application/json");
            return "{\"status\":\"404\",\"message\":\"Invalid URI\"}";

        });
        http.notFound((request, response) -> {

            response.status(404);
            response.type("application/json");
            return "{\"status\":\"404\",\"message\":\"Invalid URI\"}";

        });
    }

    void destroy() {
        http.stop();
        Bukkit.getLogger().log(Level.WARNING, "[RestPAPI] Disabled Webserver");
    }

}