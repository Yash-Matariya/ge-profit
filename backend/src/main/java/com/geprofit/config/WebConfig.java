package com.geprofit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the dashboard.
 *
 * <p>The React app is served from a different origin in development (Vite on
 * 5173), so the browser refuses to read our responses unless we say who is
 * allowed. The allowed origins are configuration rather than a wildcard --
 * {@code *} would let any website on the internet read this API through a
 * visitor's browser.
 *
 * <p>The RuneLite plugin is unaffected either way: CORS is a browser rule, and
 * an HTTP client outside a browser never sends an Origin header.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${geprofit.cors.allowed-origins:http://localhost:5173}") String origins) {
        this.allowedOrigins = origins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET")
                .maxAge(3600);
    }
}
