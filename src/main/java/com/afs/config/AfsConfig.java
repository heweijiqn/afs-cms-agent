package com.afs.config;

import com.afs.controller.filter.AuthInterceptor;
import com.afs.service.LocalMediaStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class AfsConfig implements WebMvcConfigurer {
    @Configuration(proxyBeanMethods = false)
    @Profile("!local-cms")
    @EnableScheduling
    static class SchedulingConfiguration {}

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private Environment environment;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).excludePathPatterns("/api/**").order(1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (environment.acceptsProfiles(Profiles.of("local-cms"))) {
            String location = LocalMediaStore.directory().toUri().toString();
            registry.addResourceHandler("/media/**").addResourceLocations(location.endsWith("/") ? location : location + "/");
        }
    }
}
