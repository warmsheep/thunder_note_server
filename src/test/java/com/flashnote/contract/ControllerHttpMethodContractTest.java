package com.flashnote.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerHttpMethodContractTest {

    @Test
    void flashNoteListUsesPost() throws IOException {
        assertContains("src/main/java/com/flashnote/flashnote/controller/FlashNoteController.java", "@PostMapping(\"/list\")");
    }

    @Test
    void collectionListUsesPost() throws IOException {
        assertContains("src/main/java/com/flashnote/collection/controller/CollectionController.java", "@PostMapping(\"/list\")");
    }

    @Test
    void messageListUsesPostButStreamStaysGet() throws IOException {
        assertContains("src/main/java/com/flashnote/message/controller/MessageController.java", "@PostMapping(\"/list\")");
        assertContains("src/main/java/com/flashnote/message/controller/MessageController.java", "@GetMapping(value = \"/stream\"");
    }

    @Test
    void userProfileUsesPost() throws IOException {
        assertContains("src/main/java/com/flashnote/user/controller/UserController.java", "@PostMapping(\"/profile\")");
    }

    @Test
    void syncReadEndpointsUsePost() throws IOException {
        assertContains("src/main/java/com/flashnote/sync/controller/SyncController.java", "@PostMapping(\"/pull\")");
        assertContains("src/main/java/com/flashnote/sync/controller/SyncController.java", "@PostMapping(\"/bootstrap\")");
    }

    @Test
    void favoriteEndpointsUsePostAndDelete() throws IOException {
        assertContains("src/main/java/com/flashnote/favorite/controller/FavoriteController.java", "@PostMapping(\"/list\")");
        assertContains("src/main/java/com/flashnote/favorite/controller/FavoriteController.java", "@PostMapping(\"/{messageId}\")");
        assertContains("src/main/java/com/flashnote/favorite/controller/FavoriteController.java", "@DeleteMapping(\"/{messageId}\")");
    }

    private void assertContains(String relativePath, String expectedSnippet) throws IOException {
        String content = Files.readString(Path.of(relativePath));
        assertTrue(content.contains(expectedSnippet), () -> relativePath + " should contain " + expectedSnippet);
    }
}
