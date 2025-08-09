package com.maxx_global.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.name:MaxxGlobal bayi sipariş uygulaması}")
    private String appName;

    @Value("${app.description:Ortopedi ameliyat malzemeleri için B2B sipariş yönetim sistemi}")
    private String appDescription;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components().addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title(appName)
                .description(appDescription)
                .version(appVersion)
                .contact(new Contact()
                        .name("Development Team")
                        .email("dev@ortopedi.com")
                        .url("https://www.ortopedi.com"))
                .license(new License()
                        .name("Private License")
                        .url("https://www.ortopedi.com/license"));
    }

    private List<Server> serverList() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        Server productionServer = new Server();
        productionServer.setUrl("https://api.ortopedi.com");
        productionServer.setDescription("Production Server");

        return List.of(localServer, productionServer);
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer")
                .description("JWT token için 'Bearer {token}' formatında Authorization header kullanın");
    }
}