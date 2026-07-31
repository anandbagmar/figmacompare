package io.eot.figmacompare.figma;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
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
    // Every uploadFromFigma row issues 2-3 back-to-back requests (node name, image URL,
    // image download) with no pacing at all - fine in isolation, but a run with several
    // rows can burst well past Figma's rate limit before a single 429 ever comes back to
    // trigger the retry/backoff above. Spacing every request out by at least this much
    // trades a little wall-clock time for a much lower chance of tripping the limit in
    // the first place. Not a substitute for the retry logic below - both are needed.
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(1);
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    private final String figmaToken;
    private Instant lastRequestAt;

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
                throw new RuntimeException("Figma API call failed [" + response.code() + "]: " + url
                        + " - response body: " + readBodySafely(response));
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
                throw new RuntimeException("Failed to download Figma image [" + response.code() + "]: " + imageUrl
                        + " - response body: " + readBodySafely(response));
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
                pace();
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

    /** Blocks, if needed, so consecutive requests are never closer together than MIN_REQUEST_INTERVAL. */
    private synchronized void pace() {
        if (null != lastRequestAt) {
            Duration sinceLast = Duration.between(lastRequestAt, Instant.now());
            if (sinceLast.compareTo(MIN_REQUEST_INTERVAL) < 0) {
                sleep(MIN_REQUEST_INTERVAL.minus(sinceLast));
            }
        }
        lastRequestAt = Instant.now();
    }

    private static boolean isRetryableStatus(int code) {
        return code == 429 || (code >= 500 && code < 600);
    }

    /**
     * Figma error responses normally carry a JSON body (e.g. {"status":403,"err":"..."})
     * that says exactly why - without it, a 403 is indistinguishable from an actual
     * permission problem, a stale/moved node, or a rate limit Figma chose to report as
     * 403 instead of 429. Never throws - a failure to read the body is folded into the
     * returned string instead, so it never masks the original HTTP status in the caller's
     * exception.
     */
    private static String readBodySafely(Response response) {
        try {
            String body = null == response.body() ? null : response.body().string();
            if (null == body || body.isBlank()) {
                return "(empty response body)";
            }
            return body.length() > 500 ? body.substring(0, 500) + "...(truncated)" : body;
        } catch (IOException ex) {
            return "(could not read response body: " + ex.getMessage() + ")";
        }
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
