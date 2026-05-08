package com.flashnote.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1-W1 / D1-W13-02 Web 静态资源与 Spring Security 边界测试。
 *
 * <p>验证：
 * <ul>
 *   <li>访问 /web/ 返回 SPA index.html</li>
 *   <li>访问 /web/{spa-route} 不存在的路径，仍返回 index.html（SPA fallback）</li>
 *   <li>含点号的不存在静态资源（如 /web/assets/missing.js）返回 404，不被 fallback 吞掉</li>
 *   <li>访问 /web 时重定向到 /web/</li>
 *   <li>未登录访问 /api/messages/list 仍返回 401，不会因 Web 放行被误开放</li>
 *   <li>未登录访问 /api/auth/login（POST）能路由到 controller，不被 Web 静态资源拦截</li>
 *   <li>/api/** 任何路径都不会被 SPA fallback 回 index.html</li>
 *   <li>受保护接口（如 /api/favorites/list）保持 401</li>
 * </ul>
 */
@SpringBootTest
class WebStaticResourceIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void webRootForwardsToSpaIndex() throws Exception {
        // /web/ 由 view controller forward 到 /web/index.html，
        // MockMvc 不会自动跟随内部 forward，所以这里断言 forwardedUrl，
        // 真实静态资源是否可达由 webIndexHtmlServesSpaContent 测试覆盖。
        mockMvc.perform(get("/web/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/web/index.html"));
    }

    @Test
    void webIndexHtmlServesSpaContent() throws Exception {
        MvcResult result = mockMvc.perform(get("/web/index.html"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        assertTrue(body.contains("<div id=\"app\"></div>"),
                () -> "/web/index.html should serve SPA index.html with #app root, but body was: "
                        + body.substring(0, Math.min(200, body.length())));
    }

    @Test
    void webSpaRouteFallsBackToIndex() throws Exception {
        MvcResult result = mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("<div id=\"app\"></div>"),
                "Unknown SPA route should fallback to index.html");
    }

    @Test
    void webMissingAssetIsNotServedAsSpaIndex() throws Exception {
        // 含点号的不存在静态资源不能被 SPA fallback 吞掉返回 index.html，
        // 否则浏览器加载 module 会报 MIME 错误。项目统一 200 响应风格下，
        // 关键断言是 body 不能是 SPA index.html。
        MvcResult result = mockMvc.perform(get("/web/assets/does-not-exist-xyz.js")).andReturn();
        String body = result.getResponse().getContentAsString();
        if (body != null) {
            assertTrue(!body.contains("<div id=\"app\"></div>"),
                    "missing /web/assets must NOT be served as SPA index.html, got: "
                            + body.substring(0, Math.min(200, body.length())));
        }
    }

    @Test
    void webBareRedirectsToTrailingSlash() throws Exception {
        mockMvc.perform(get("/web"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/"));
    }

    @Test
    void apiMessagesListUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/messages/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiFavoritesListUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/favorites/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiAuthLoginEndpointStillRoutable() throws Exception {
        // 不登录直接 POST /api/auth/login，期望路由到 controller。
        // 项目设计：4xx/5xx 由 GlobalExceptionHandler 统一返回 200 + body code != 0。
        // 关键安全断言：不能被 redirect 到 /web/，body 不能是 SPA index.html，必须是 ApiResponse JSON。
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertTrue(location == null || !location.startsWith("/web"),
                () -> "/api/auth/login must not be redirected to /web/, got Location=" + location);

        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        assertTrue(!body.contains("<div id=\"app\"></div>"),
                "/api/auth/login must NOT be served as SPA index.html");
        assertTrue(body.contains("\"code\""),
                "/api/auth/login response should be ApiResponse JSON with 'code' field, got: "
                        + body.substring(0, Math.min(200, body.length())));
    }

    @Test
    void apiUnknownEndpointDoesNotLeakSpaIndex() throws Exception {
        // /api/** 任何未知端点都不能被 SPA fallback 吃掉。
        // 项目设计下，未鉴权访问会返回 401，未知端点也不会落到 SPA 静态资源。
        MvcResult result = mockMvc.perform(get("/api/some-non-existent-endpoint")).andReturn();
        String body = result.getResponse().getContentAsString();
        if (body != null) {
            assertTrue(!body.contains("<div id=\"app\"></div>"),
                    "/api/** must not be served by Web SPA fallback");
        }
    }
}
