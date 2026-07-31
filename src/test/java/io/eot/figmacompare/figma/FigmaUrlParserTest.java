package io.eot.figmacompare.figma;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class FigmaUrlParserTest {

    @Test
    public void parsesFileKeyAndNodeIdFromDesignUrl() {
        FigmaUrlInfo info = FigmaUrlParser.parse(
                "https://www.figma.com/design/7kPt5byFnDm1hs2Bd1FlNL/vodQA?node-id=170-57&t=abc-0");
        assertEquals(info.getFileKey(), "7kPt5byFnDm1hs2Bd1FlNL");
        assertEquals(info.getNodeId(), "170:57");
    }

    @Test
    public void parsesLegacyFileUrlVariant() {
        FigmaUrlInfo info = FigmaUrlParser.parse("https://www.figma.com/file/abc123/SomeFile?node-id=1-2");
        assertEquals(info.getFileKey(), "abc123");
        assertEquals(info.getNodeId(), "1:2");
    }

    @Test
    public void urlDecodesAnAlreadyColonSeparatedNodeId() {
        FigmaUrlInfo info = FigmaUrlParser.parse("https://www.figma.com/design/abc/Name?node-id=170%3A57");
        assertEquals(info.getNodeId(), "170:57");
    }

    @Test
    public void throwsWhenFileKeyIsMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> FigmaUrlParser.parse("https://www.figma.com/not-a-file-url"));
    }

    @Test
    public void throwsWhenNodeIdIsMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> FigmaUrlParser.parse("https://www.figma.com/design/abc123/Name"));
    }
}
