package me.fredthedoggy.restpapi;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import spark.Response;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestSecurityTest {
    private YamlConfiguration config() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tokens", Collections.singletonList("12345678-1234-1234-1234-123456789abc"));
        return yaml;
    }

    @Test
    void tokensMustMatchExactly() {
        SparkWrapper server = new SparkWrapper(mock(Restpapi.class), new RestConfig(config()));
        assertTrue(server.authorized("12345678-1234-1234-1234-123456789abc"));
        assertFalse(server.authorized("prefix12345678-1234-1234-1234-123456789abc"));
        assertFalse(server.authorized("12345678-1234-1234-1234-123456789abc suffix"));
        assertFalse(server.authorized(null));
        assertFalse(server.authorized(""));
    }

    @Test
    void invalidOrMissingTokensFailClosed() {
        YamlConfiguration yaml = config();
        yaml.set("tokens", Arrays.asList("valid-valid-valid-valid", ""));
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
        yaml.set("tokens", Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
        yaml.set("tokens", Collections.singletonList("short"));
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
    }

    @Test
    void serializesPlaceholderTextAndKeepsLegacyStatus() {
        Response response = mock(Response.class);
        String message = "Name: \"Thiago\" \\ test\nline";
        String payload = SparkWrapper.json(response, 200, message);
        assertEquals(message, JsonParser.parseString(payload).getAsJsonObject().get("message").getAsString());
        assertEquals("200", JsonParser.parseString(payload).getAsJsonObject().get("status").getAsString());
        verify(response).type("application/json");
        verify(response).status(200);
        assertEquals("", JsonParser.parseString(SparkWrapper.json(response, 200, ""))
                .getAsJsonObject().get("message").getAsString());
    }

    @Test
    void limitsRequestsPerPeer() {
        RequestLimiter limiter = new RequestLimiter(2, 60);
        assertTrue(limiter.allow("192.0.2.1"));
        assertTrue(limiter.allow("192.0.2.1"));
        assertFalse(limiter.allow("192.0.2.1"));
        assertTrue(limiter.allow("192.0.2.2"));
    }

    @Test
    void distinguishesUnknownPlaceholderFromEmptyValue() {
        assertEquals(406, SparkWrapper.placeholderStatus("%unknown%", "%unknown%"));
        assertEquals(200, SparkWrapper.placeholderStatus("%known%", ""));
        assertEquals(200, SparkWrapper.placeholderStatus("%known%", "value"));
    }

    @Test
    void rejectsOutOfRangeNetworkSettings() {
        YamlConfiguration yaml = config();
        yaml.set("port", 65536);
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
        yaml.set("port", 8080);
        yaml.set("max-concurrent", 1000);
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
    }

    @Test
    void shutdownIsIdempotentBeforeHttpStartup() {
        SparkWrapper server = new SparkWrapper(mock(Restpapi.class), new RestConfig(config()));
        assertDoesNotThrow(server::stop);
        assertDoesNotThrow(server::stop);
    }
}
