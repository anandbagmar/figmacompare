package io.eot.figmacompare.figma;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FigmaUrlParser {

    private static final Pattern FILE_KEY_PATTERN = Pattern.compile("figma\\.com/(?:file|design)/([a-zA-Z0-9]+)/");
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("node-id=([^&]+)");

    private FigmaUrlParser() {
    }

    public static FigmaUrlInfo parse(String figmaUrl) {
        Matcher fileKeyMatcher = FILE_KEY_PATTERN.matcher(figmaUrl);
        if (!fileKeyMatcher.find()) {
            throw new IllegalArgumentException("Could not find a Figma file key in URL: " + figmaUrl);
        }
        String fileKey = fileKeyMatcher.group(1);

        Matcher nodeIdMatcher = NODE_ID_PATTERN.matcher(figmaUrl);
        if (!nodeIdMatcher.find()) {
            throw new IllegalArgumentException("Figma URL does not contain a node-id (share a specific "
                    + "frame/component via Figma's 'Copy link to selection'): " + figmaUrl);
        }
        String rawNodeId = java.net.URLDecoder.decode(nodeIdMatcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
        String nodeId = rawNodeId.replace("-", ":");

        return new FigmaUrlInfo(fileKey, nodeId);
    }
}
