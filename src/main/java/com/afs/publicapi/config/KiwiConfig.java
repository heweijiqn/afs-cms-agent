package com.afs.publicapi.config;

import com.afs.publicapi.controller.filter.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("!agent-sim")
@ComponentScan(basePackages = {"com.afs.publicapi.controller", "com.afs.publicapi.service"},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@MapperScan(basePackages = "com.afs.publicapi.dao", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class KiwiConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").order(1);
    }
}
