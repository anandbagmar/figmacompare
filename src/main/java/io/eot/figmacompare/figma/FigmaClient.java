package io.eot.figmacompare.figma;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FigmaClient {

    private static final String FIGMA_API_BASE = "https://api.figma.com/v1";
    private static final Gson GSON = new Gson();
    // Figma renders the export server-side on first request, which can take well over
    // OkHttp's 10s default read timeout for large/complex frames.
    private static final int MAX_ATTEMPTS = 6;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration INITIAL_RATE_LIMIT_DELAY = Duration.ofSeconds(15);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(60);
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    private final String figmaToken;

    public FigmaClient(String figmaToken) {
        if (null == figmaToken || figmaToken.isBlank()) {
            throw new IllegalArgumentException("Figma token must not be null/blank");
        }
        this.figmaToken = figmaToken;
    }

    public File getCachedImage(String figmaUrl, String format, String scale, String cacheDir, boolean forceRefresh) {
        FigmaUrlInfo urlInfo = FigmaUrlParser.parse(figmaUrl);
        String sanitizedNodeId = urlInfo.getNodeId().replace(":", "-");
        File cacheFile = new File(cacheDir,
                urlInfo.getFileKey() + "_" + sanitizedNodeId + "_" + scale + "x." + format);

        if (cacheFile.exists() && !forceRefresh) {
            System.out.println("Using cached Figma image: " + cacheFile.getAbsolutePath());
            return cacheFile;
        }

        String imageUrl = fetchImageUrl(urlInfo.getFileKey(), urlInfo.getNodeId(), format, scale);
        downloadTo(imageUrl, cacheFile);
        return cacheFile;
    }

    public String fetchNodeName(String figmaUrl) {
        FigmaUrlInfo urlInfo = FigmaUrlParser.parse(figmaUrl);
        String url = FIGMA_API_BASE + "/files/" + urlInfo.getFileKey() + "/nodes?ids=" + urlInfo.getNodeId();
        JsonObject response = get(url);
        JsonObject nodes = response.getAsJsonObject("nodes");
        JsonObject node = nodes.getAsJsonObject(urlInfo.getNodeId());
        if (null == node) {
            throw new RuntimeException("Figma node " + urlInfo.getNodeId() + " not found in file "
                    + urlInfo.getFileKey());
        }
        return node.getAsJsonObject("document").get("name").getAsString();
    }

    private String fetchImageUrl(String fileKey, String nodeId, String format, String scale) {
        String url = FIGMA_API_BASE + "/images/" + fileKey + "?ids=" + nodeId + "&format=" + format + "&scale="
                + scale;
        JsonObject response = get(url);
        if (response.has("err") && !response.get("err").isJsonNull()) {
            throw new RuntimeException("Figma image API error for node " + nodeId + ": " + response.get("err"));
        }
        JsonObject images = response.getAsJsonObject("images");
        for (Map.Entry<String, com.google.gson.JsonElement> entry : images.entrySet()) {
            if (entry.getKey().equals(nodeId) && !entry.getValue().isJsonNull()) {
                return entry.getValue().getAsString();
            }
        }
        throw new RuntimeException("Figma did not return a renderable image URL for node " + nodeId);
    }

    private JsonObject get(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("X-Figma-Token", figmaToken)
                .build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Figma API call failed [" + response.code() + "]: " + url);
            }
            return GSON.fromJson(response.body().string(), JsonObject.class);
        } catch (IOException ex) {
            throw new RuntimeException("Figma API call failed: " + url, ex);
        }
    }

    private void downloadTo(String imageUrl, File destination) {
        Request request = new Request.Builder().url(imageUrl).build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to download Figma image [" + response.code() + "]: " + imageUrl);
            }
            Files.createDirectories(destination.getParentFile().toPath());
            try (FileOutputStream out = new FileOutputStream(destination)) {
                out.write(response.body().bytes());
            }
            System.out.println("Downloaded Figma image to cache: " + destination.getAbsolutePath());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to download Figma image: " + imageUrl, ex);
        }
    }

    /**
     * Retries on network-level failures (timeouts, connection resets) and on HTTP 429
     * (rate limited) / 5xx (transient server error) responses - not on other HTTP error
     * responses (4xx like 403/404), which retrying won't fix. Uses exponential backoff,
     * honoring a Retry-After header when Figma sends one on a 429.
     */
    private Response executeWithRetry(Request request) throws IOException {
        IOException lastFailure = null;
        Duration delay = INITIAL_RETRY_DELAY;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Response response = null;
            try {
                response = httpClient.newCall(request).execute();
                if (response.isSuccessful() || !isRetryableStatus(response.code()) || attempt == MAX_ATTEMPTS) {
                    return response;
                }
                Duration wait = retryDelayFor(response, attempt == 1 ? INITIAL_RATE_LIMIT_DELAY : delay);
                System.out.println("Request to " + request.url() + " got HTTP " + response.code() + " (attempt "
                        + attempt + "/" + MAX_ATTEMPTS + "). Retrying in " + wait.getSeconds() + "s...");
                response.close();
                sleep(wait);
                delay = delay.multipliedBy(2);
            } catch (IOException ex) {
                if (null != response) {
                    response.close();
                }
                lastFailure = ex;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                System.out.println("Request to " + request.url() + " failed (attempt " + attempt + "/"
                        + MAX_ATTEMPTS + "): " + ex + ". Retrying in " + delay.getSeconds() + "s...");
                sleep(delay);
                delay = delay.multipliedBy(2);
            }
        }
        throw lastFailure;
    }

    private static boolean isRetryableStatus(int code) {
        return code == 429 || (code >= 500 && code < 600);
    }

    private static Duration retryDelayFor(Response response, Duration fallback) {
        String retryAfter = response.header("Retry-After");
        if (null != retryAfter) {
            try {
                Duration parsed = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
                // Figma has been observed sending Retry-After values not actually in
                // seconds (e.g. milliseconds) - never trust it past a sane ceiling, so a
                // misinterpreted unit can't stall the whole run for hours.
                return parsed.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : parsed;
            } catch (NumberFormatException ignored) {
                // Fall through to the fallback delay below.
            }
        }
        return fallback;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry Figma request", ex);
        }
    }
}
