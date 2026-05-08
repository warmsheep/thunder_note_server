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
 * D1-W1 Web 用户端静态资源与 SPA fallback 配置。
 *
 * <p>当前 Spring Boot 服务同时承担 Android API 与 Web 页面：
 * <ul>
 *   <li>Web 工程构建产物位于 classpath:/static/web/，浏览器入口为 /web/</li>
 *   <li>静态资源命中（如 /web/assets/index-xxx.js）按真实文件返回</li>
 *   <li>SPA 内部路由（如 /web/login）由 PathResourceResolver fallback 回 /web/index.html</li>
 * </ul>
 *
 * <p>不影响 /api/**、/actuator/**、根路径默认资源处理；这些路径仍由各自的 controller 或
 * Spring Boot 默认资源处理器处理。
 */
@Configuration
public class WebStaticResourceConfig implements WebMvcConfigurer {

    private static final String WEB_BASE_LOCATION = "classpath:/static/web/";
    private static final String WEB_INDEX_LOCATION = "/static/web/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/web/**")
                .addResourceLocations(WEB_BASE_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver(WEB_INDEX_LOCATION));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 用户访问 /web 时（无尾斜杠），统一重定向到 /web/，让前端路由从根开始解析
        registry.addRedirectViewController("/web", "/web/");
        // Spring 6 中 ResourceHttpRequestHandler 对空 resourcePath 直接返回 null，
        // 因此 /web/ 必须显式 forward 到 /web/index.html，让资源链找到真实文件。
        registry.addViewController("/web/").setViewName("forward:/web/index.html");
    }

    private static final class SpaFallbackResolver extends PathResourceResolver {
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
    }
}
