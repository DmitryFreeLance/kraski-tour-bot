package ru.kraskitour.bot.max;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class MaxApiClient {
    private static final Logger log = LoggerFactory.getLogger(MaxApiClient.class);
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;

    private final String token;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public MaxApiClient(String token, String baseUrl) {
        this.token = token;
        this.baseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.mapper = new ObjectMapper();
    }

    public static class UpdatesPage {
        public final ArrayNode updates;
        public final Long marker;

        public UpdatesPage(ArrayNode updates, Long marker) {
            this.updates = updates;
            this.marker = marker;
        }
    }

    public UpdatesPage getUpdates(Long marker, int timeoutSec, int limit) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(baseUrl).append("/updates")
                .append("?timeout=").append(timeoutSec)
                .append("&limit=").append(limit);
        if (marker != null) {
            url.append("&marker=").append(marker);
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                .header("Authorization", token)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("GET /updates failed: " + resp.statusCode() + " " + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        ArrayNode updates = root.has("updates") && root.get("updates").isArray()
                ? (ArrayNode) root.get("updates")
                : mapper.createArrayNode();
        Long nextMarker = root.hasNonNull("marker") ? root.get("marker").asLong() : null;
        return new UpdatesPage(updates, nextMarker);
    }

    public JsonNode sendMessage(Long chatId, Long userId, ObjectNode body) {
        return sendMessageWithRetry(chatId, userId, body, DEFAULT_RETRY_ATTEMPTS);
    }

    private JsonNode sendMessageWithRetry(Long chatId, Long userId, ObjectNode body, int attempts) {
        int tries = 0;
        long backoffMs = 700;
        while (true) {
            tries++;
            try {
                String query = chatId != null
                        ? "chat_id=" + urlEncode(String.valueOf(chatId))
                        : "user_id=" + urlEncode(String.valueOf(userId));
                String url = baseUrl + "/messages?" + query;

                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    return mapper.readTree(resp.body());
                }

                String code = extractErrorCode(resp.body());
                if ("attachment.not.ready".equals(code) && tries < attempts) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                log.warn("Send message failed: {} {}", resp.statusCode(), resp.body());
                return null;
            } catch (Exception e) {
                if (tries >= attempts) {
                    log.warn("Send message error after retries", e);
                    return null;
                }
                sleep(backoffMs);
                backoffMs *= 2;
            }
        }
    }

    public boolean answerCallback(String callbackId, ObjectNode body) {
        try {
            String url = baseUrl + "/answers?callback_id=" + urlEncode(callbackId);
            ObjectNode payload = (body == null) ? mapper.createObjectNode() : body;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) return true;
            log.warn("Answer callback failed: {} {}", resp.statusCode(), resp.body());
            return false;
        } catch (Exception e) {
            log.warn("Answer callback error", e);
            return false;
        }
    }

    public ObjectNode uploadImage(byte[] data, String filename, String contentType) throws IOException, InterruptedException {
        String url = baseUrl + "/uploads?type=image";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("POST /uploads failed: " + resp.statusCode() + " " + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        String uploadUrl = root.path("url").asText(null);
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new IOException("Upload URL is empty");
        }

        String boundary = "----MaxBotBoundary" + UUID.randomUUID();
        byte[] multipart = buildMultipart(boundary, filename, contentType, data);

        HttpRequest uploadReq = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Authorization", token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                .build();
        HttpResponse<String> uploadResp = http.send(uploadReq, HttpResponse.BodyHandlers.ofString());
        if (uploadResp.statusCode() / 100 != 2) {
            throw new IOException("Upload failed: " + uploadResp.statusCode() + " " + uploadResp.body());
        }
        JsonNode payload = mapper.readTree(uploadResp.body());
        if (payload.isObject()) return (ObjectNode) payload;
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.set("payload", payload);
        return wrapper;
    }

    private static byte[] buildMultipart(String boundary, String filename, String contentType, byte[] data) throws IOException {
        String safeName = (filename == null || filename.isBlank()) ? "file" : filename;
        String safeType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"data\"; filename=\"" + safeName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + safeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String extractErrorCode(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = new ObjectMapper().readTree(body);
            JsonNode code = root.get("code");
            if (code != null && code.isTextual()) return code.asText();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
