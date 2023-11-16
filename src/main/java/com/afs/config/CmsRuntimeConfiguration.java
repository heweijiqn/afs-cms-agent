package com.afs.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("!agent-sim")
@ComponentScan({"com.afs.config", "com.afs.controller", "com.afs.dao", "com.afs.service", "com.afs.task", "com.afs.utils"})
@MapperScan("com.afs.dao")
public class CmsRuntimeConfiguration {
    @Bean
    public RestTemplate restTemplate(Environment environment) {
        RestTemplate template = new RestTemplate();
        if (environment.acceptsProfiles(Profiles.of("local-cms"))) {
            template.getInterceptors().add((request, body, execution) -> {
                throw new java.io.IOException("Outbound HTTP is disabled in local CMS");
            });
        }
        return template;
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableWebServerFactory> webServerFactoryCustomizer() {
        return factory -> factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/index.html"));
    }
}
