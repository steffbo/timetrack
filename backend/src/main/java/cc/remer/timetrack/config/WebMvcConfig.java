package cc.remer.timetrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC configuration for serving the Vue.js SPA.
 * <p>
 * This configuration handles routing for the single-page application by:
 * 1. Serving static resources from /app/static (where the frontend build is copied in the Docker container)
 * 2. Falling back to index.html for any non-API routes (SPA routing)
 * <p>
 * API routes (/api/**) are not affected and continue to work normally.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static resources from both classpath:/static/ and file:/app/static/
        // This allows the app to work both in development (classpath) and production (external directory)
        registry.addResourceHandler("/**")
                .addResourceLocations("file:/app/static/", "classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);

                        // If the resource exists, return it
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // For SPA routing: if the request path doesn't start with /api/
                        // and the resource doesn't exist, fall back to index.html
                        if (!resourcePath.startsWith("api/")) {
                            // Try to return index.html from the same location
                            Resource indexResource = location.createRelative("index.html");
                            if (indexResource.exists() && indexResource.isReadable()) {
                                return indexResource;
                            }

                            // Fallback to classpath:/static/index.html
                            Resource classpathIndex = new ClassPathResource("static/index.html");
                            if (classpathIndex.exists() && classpathIndex.isReadable()) {
                                return classpathIndex;
                            }
                        }

                        return null;
                    }
                });
    }
}
