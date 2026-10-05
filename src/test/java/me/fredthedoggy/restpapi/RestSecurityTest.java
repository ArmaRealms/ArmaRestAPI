package me.fredthedoggy.restpapi;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RestSecurityTest {
    private YamlConfiguration config() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tokens", Collections.singletonList("12345678-1234-1234-1234-123456789abc"));
        return yaml;
    }

    @Test
    void tokensMustMatchExactly() {
        JettyServer server = new JettyServer(mock(Restpapi.class), new RestConfig(config()));
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
        String message = "Name: \"Thiago\" \\ test\nline";
        String payload = JettyServer.jsonPayload(200, message);
        assertEquals(message, JsonParser.parseString(payload).getAsJsonObject().get("message").getAsString());
        assertEquals("200", JsonParser.parseString(payload).getAsJsonObject().get("status").getAsString());
        assertEquals("", JsonParser.parseString(JettyServer.jsonPayload(200, ""))
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
        assertEquals(406, JettyServer.placeholderStatus("%unknown%", "%unknown%"));
        assertEquals(200, JettyServer.placeholderStatus("%known%", ""));
        assertEquals(200, JettyServer.placeholderStatus("%known%", "value"));
    }

    @Test
    void rejectsOutOfRangeNetworkSettings() {
        YamlConfiguration yaml = config();
        yaml.set("port", 65536);
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
        yaml.set("port", 8080);
        yaml.set("max-concurrent", 1000);
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
        yaml.set("max-concurrent", 16);
        yaml.set("shutdown-timeout-ms", 0);
        assertThrows(IllegalArgumentException.class, () -> new RestConfig(yaml));
    }

    @Test
    void shutdownIsIdempotentBeforeHttpStartup() {
        JettyServer server = new JettyServer(mock(Restpapi.class), new RestConfig(config()));
        assertDoesNotThrow(server::stop);
        assertDoesNotThrow(server::stop);
    }
}
