package com.flashnote.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperScanContractTest {

    @Test
    void mapperScan_shouldOnlyTargetMapperPackages() throws IOException {
        String content = Files.readString(Path.of("src/main/java/com/flashnote/FlashNoteApplication.java"));

        assertTrue(content.contains("com.flashnote.auth.mapper"),
                "MapperScan should explicitly include mapper packages");
        assertTrue(!content.contains("@MapperScan(\"com.flashnote\")"),
                "MapperScan should not scan the entire com.flashnote package");
    }
}
