package com.maxx_global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;
import java.util.logging.Logger;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = Logger.getLogger(WebConfig.class.getName());

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.file.static-url-pattern:/uploads/**}")
    private String staticUrlPattern;

    @Value("${app.file.cache-period:3600}")
    private Integer cachePeriod;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            // Uploads klasörünün mutlak yolunu al
            File uploadDirectory = new File(uploadDir);
            if (!uploadDirectory.exists()) {
                boolean created = uploadDirectory.mkdirs();
                logger.info("Upload directory created: " + created + " - " + uploadDirectory.getAbsolutePath());
            }

            String absolutePath = uploadDirectory.getAbsolutePath();
            String resourceLocation = "file:" + absolutePath + "/";

            // Static resource handler ekle
            registry.addResourceHandler(staticUrlPattern)
                    .addResourceLocations(resourceLocation)
                    .setCachePeriod(cachePeriod)
                    .resourceChain(true);

            logger.info("✅ Static file serving configured:");
            logger.info("   URL Pattern: " + staticUrlPattern);
            logger.info("   Resource Location: " + resourceLocation);
            logger.info("   Cache Period: " + cachePeriod + " seconds");
            logger.info("   Upload Directory: " + absolutePath);

        } catch (Exception e) {
            logger.severe("❌ Failed to configure static file serving: " + e.getMessage());
            throw new RuntimeException("Static dosya servisi yapılandırılamadı", e);
        }
    }
}