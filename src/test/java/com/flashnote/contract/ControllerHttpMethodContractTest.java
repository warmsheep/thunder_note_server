package com.flashnote.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1-W13-03 Android API 回归契约测试。
 *
 * <p>这个套件锁住所有 Android 当前真实主链 + Web 当前实现共同依赖的核心 controller
 * 路径与 HTTP 方法。Web 接入与重构（W1 / W4.1 / W12 / W13）全程不允许改动这些契约；
 * 任何路径或 HTTP 方法变更必须主动改这里，强制和 `docs/API接口设计.md`
 * 同步走完文档回写流程。
 *
 * <p>覆盖端点：
 * <ul>
 *   <li>auth：login / register / refresh / logout / password / gesture-lock(GET/PUT/DELETE)</li>
 *   <li>user：profile(POST/PUT) / avatar(PUT) / contacts(GET) / contacts/search(GET) /
 *       contacts/requests(GET) / contacts/requests/count(GET) /
 *       contacts/request(POST) / contacts/request/accept(POST) / contacts/request/reject(POST) /
 *       contacts/request/{id}(DELETE) / contacts/{id}(DELETE)</li>
 *   <li>flashNote：list(POST)</li>
 *   <li>collection：list(POST)</li>
 *   <li>message：list(POST) / stream(GET)</li>
 *   <li>sync：bootstrap(POST) / pull(POST)</li>
 *   <li>favorite：list(POST) / {id}(POST/DELETE)</li>
 *   <li>file：upload(POST) / download(GET)</li>
 * </ul>
 *
 * <p>测试本身只读 controller 源码做字符串断言，不启动 Spring Context，跑得很快。
 */
class ControllerHttpMethodContractTest {

    private static final String AUTH = "src/main/java/com/flashnote/auth/controller/AuthController.java";
    private static final String USER = "src/main/java/com/flashnote/user/controller/UserController.java";
    private static final String FLASH_NOTE = "src/main/java/com/flashnote/flashnote/controller/FlashNoteController.java";
    private static final String COLLECTION = "src/main/java/com/flashnote/collection/controller/CollectionController.java";
    private static final String MESSAGE = "src/main/java/com/flashnote/message/controller/MessageController.java";
    private static final String SYNC = "src/main/java/com/flashnote/sync/controller/SyncController.java";
    private static final String FAVORITE = "src/main/java/com/flashnote/favorite/controller/FavoriteController.java";
    private static final String FILE = "src/main/java/com/flashnote/file/controller/FileController.java";

    @Test
    void authEndpointsContract() throws IOException {
        assertContains(AUTH, "@RequestMapping(\"/api/auth\")");
        assertContains(AUTH, "@PostMapping(\"/login\")");
        assertContains(AUTH, "@PostMapping(\"/register\")");
        assertContains(AUTH, "@PostMapping(\"/refresh\")");
        assertContains(AUTH, "@PostMapping(\"/logout\")");
        assertContains(AUTH, "@PutMapping(\"/password\")");
        // gesture-lock：Android 当前能力，Web v1 显式不实现，但路径不能漂移
        assertContains(AUTH, "@PutMapping(\"/gesture-lock\")");
        assertContains(AUTH, "@GetMapping(\"/gesture-lock\")");
        assertContains(AUTH, "@DeleteMapping(\"/gesture-lock\")");
    }

    @Test
    void userEndpointsContract() throws IOException {
        assertContains(USER, "@RequestMapping(\"/api/users\")");
        assertContains(USER, "@PostMapping(\"/profile\")");
        assertContains(USER, "@PutMapping(\"/profile\")");
        assertContains(USER, "@PutMapping(\"/avatar\")");
    }

    @Test
    void userContactsEndpointsContract() throws IOException {
        assertContains(USER, "@GetMapping(\"/contacts\")");
        assertContains(USER, "@GetMapping(\"/contacts/search\")");
        assertContains(USER, "@GetMapping(\"/contacts/requests\")");
        assertContains(USER, "@GetMapping(\"/contacts/requests/count\")");
        assertContains(USER, "@PostMapping(\"/contacts/request\")");
        assertContains(USER, "@PostMapping(\"/contacts/request/accept\")");
        assertContains(USER, "@PostMapping(\"/contacts/request/reject\")");
        assertContains(USER, "@DeleteMapping(\"/contacts/request/{requestId}\")");
        assertContains(USER, "@DeleteMapping(\"/contacts/{contactUserId}\")");
    }

    @Test
    void flashNoteListUsesPost() throws IOException {
        assertContains(FLASH_NOTE, "@PostMapping(\"/list\")");
    }

    @Test
    void collectionListUsesPost() throws IOException {
        assertContains(COLLECTION, "@PostMapping(\"/list\")");
    }

    @Test
    void messageListUsesPostButStreamStaysGet() throws IOException {
        assertContains(MESSAGE, "@PostMapping(\"/list\")");
        assertContains(MESSAGE, "@GetMapping(value = \"/stream\"");
    }

    @Test
    void userProfileUsesPost() throws IOException {
        assertContains(USER, "@PostMapping(\"/profile\")");
    }

    @Test
    void syncReadEndpointsUsePost() throws IOException {
        assertContains(SYNC, "@PostMapping(\"/pull\")");
        assertContains(SYNC, "@PostMapping(\"/bootstrap\")");
    }

    @Test
    void favoriteEndpointsUsePostAndDelete() throws IOException {
        assertContains(FAVORITE, "@PostMapping(\"/list\")");
        assertContains(FAVORITE, "@PostMapping(\"/{messageId}\")");
        assertContains(FAVORITE, "@DeleteMapping(\"/{messageId}\")");
    }

    @Test
    void fileEndpointsContract() throws IOException {
        assertContains(FILE, "@RequestMapping(\"/api/files\")");
        // upload 必须是 POST + multipart；download 必须是 GET + RequestParam("objectName")
        assertContains(FILE, "@PostMapping(\"/upload\")");
        assertContains(FILE, "@RequestParam(\"file\")");
        assertContains(FILE, "@GetMapping(\"/download\")");
        assertContains(FILE, "@RequestParam(\"objectName\")");
    }

    private void assertContains(String relativePath, String expectedSnippet) throws IOException {
        String content = Files.readString(Path.of(relativePath));
        assertTrue(content.contains(expectedSnippet), () -> relativePath + " should contain " + expectedSnippet);
    }
}
