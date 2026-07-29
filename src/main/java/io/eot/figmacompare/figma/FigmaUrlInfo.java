package io.eot.figmacompare.figma;

public class FigmaUrlInfo {

    private final String fileKey;
    private final String nodeId;

    public FigmaUrlInfo(String fileKey, String nodeId) {
        this.fileKey = fileKey;
        this.nodeId = nodeId;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getNodeId() {
        return nodeId;
    }
}
