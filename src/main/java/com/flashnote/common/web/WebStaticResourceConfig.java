package com.flashnote.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * D1-W1 / D1-W4.1 Web 用户端静态资源与 SPA fallback 配置。
 *
 * <p>当前 Spring Boot 服务同时承担 Android API 与 Web 页面：
 * <ul>
 *   <li>Web 工程构建产物位于 classpath:/static/web/，浏览器入口为 /（不再使用 /web/ 前缀）</li>
 *   <li>真实静态资源（如 /assets/index-xxx.js、/index.html、/favicon.ico）由 ResourceHandler
 *       从 classpath:/static/web/ 下命中</li>
 *   <li>SPA 内部路由（如 /login、/notes）由 SpaFallbackResolver fallback 回 /static/web/index.html</li>
 *   <li>/api/**、/actuator/**、/error 等保留前缀不会被 SPA fallback 吞掉，仍 404 由各自 handler 处理</li>
 * </ul>
 */
@Configuration
public class WebStaticResourceConfig implements WebMvcConfigurer {

    private static final String WEB_BASE_LOCATION = "classpath:/static/web/";
    private static final String WEB_INDEX_LOCATION = "/static/web/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 / 暴露为 SPA 根：先尝试真实静态资源，再 fallback 到 index.html。
        // 排除 api/、actuator/、error 等保留前缀，避免 SPA 吞掉这些应该走 controller / 错误处理的路径。
        registry.addResourceHandler("/**")
                .addResourceLocations(WEB_BASE_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver(WEB_INDEX_LOCATION));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Spring 6 中 ResourceHttpRequestHandler 对空 resourcePath 直接返回 null，
        // 因此 / 必须显式 forward 到 /index.html，让资源链找到真实文件。
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    private static final class SpaFallbackResolver extends PathResourceResolver {
        private static final String[] RESERVED_PREFIXES = { "api/", "actuator/" };
        private static final String[] RESERVED_PATHS = { "error" };

        private final String fallbackLocation;

        private SpaFallbackResolver(String fallbackLocation) {
            this.fallbackLocation = fallbackLocation;
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (isReservedPath(resourcePath)) {
                // 让 Spring 走默认 404 链路（NoResourceFoundException 被 GlobalExceptionHandler 兜底成 404）
                return null;
            }
            // 含点号的路径通常是真实静态资源请求（.js/.css/.map 等），找不到就让 Spring 返回 404，
            // 避免误把 index.html 内容塞回浏览器导致 MIME 类型错误或前端加载失败。
            if (resourcePath != null && resourcePath.contains(".")) {
                return null;
            }
            // 其他路径视为 SPA 内部路由，fallback 到 index.html，由前端 vue-router 接管。
            ClassPathResource fallback = new ClassPathResource(fallbackLocation);
            if (fallback.exists() && fallback.isReadable()) {
                return fallback;
            }
            return null;
        }

        private static boolean isReservedPath(String resourcePath) {
            if (resourcePath == null) {
                return false;
            }
            for (String prefix : RESERVED_PREFIXES) {
                if (resourcePath.startsWith(prefix)) {
                    return true;
                }
            }
            for (String reserved : RESERVED_PATHS) {
                if (resourcePath.equals(reserved)) {
                    return true;
                }
            }
            return false;
        }
    }
}
