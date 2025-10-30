package com.afs.agent;

import com.zaxxer.hikari.HikariDataSource;
import java.net.InetAddress;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("agent-sim")
@ComponentScan(basePackageClasses = AgentSimulationConfiguration.class)
public class AgentSimulationConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(Environment environment, @Value("${spring.datasource.url}") String url) {
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> !profile.equals("agent-sim"))) {
            throw new IllegalStateException("Run the agent-sim Spring profile on its own");
        }
        if (!url.matches("jdbc:h2:(file|mem):[^;]+(;WRITE_DELAY=0)?")) {
            throw new IllegalStateException("agent-sim requires a local H2 database");
        }
        var pool = new HikariDataSource();
        pool.setDriverClassName("org.h2.Driver");
        pool.setJdbcUrl(url);
        pool.setUsername("sa");
        pool.setPassword("");
        return pool;
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableWebServerFactory> agentLoopback() {
        return factory -> factory.setAddress(InetAddress.getLoopbackAddress());
    }
}
