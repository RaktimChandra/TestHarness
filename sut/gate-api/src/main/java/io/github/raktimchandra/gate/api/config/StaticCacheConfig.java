package io.github.raktimchandra.gate.api.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Caching of the UI. Vite names every built asset after its content hash
 * ({@code /assets/index-BX9SCRRv.js}), so an asset URL never changes meaning: those are cached
 * for a year and marked immutable. Everything else keeps Spring Security's {@code no-store}
 * (API responses carry personal data; {@code index.html} must always point at the current
 * assets). Spring Security does not overwrite a Cache-Control header the application set.
 */
@Configuration
public class StaticCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/META-INF/resources/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
