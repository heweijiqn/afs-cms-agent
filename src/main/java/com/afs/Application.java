package com.afs;

import com.afs.agent.AgentSimulationConfiguration;
import com.afs.agent.LocalWorkspaceConfiguration;
import com.afs.config.CmsRuntimeConfiguration;
import com.afs.publicapi.config.KiwiConfig;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@Import({CmsRuntimeConfiguration.class, AgentSimulationConfiguration.class, LocalWorkspaceConfiguration.class, KiwiConfig.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.addInitializers(context -> {
            var environment = context.getEnvironment();
            var profiles = environment.getActiveProfiles();
            boolean localCms = Arrays.equals(profiles, new String[]{"local-cms"});
            boolean local = localCms || Arrays.equals(profiles, new String[]{"agent-sim"});
            if (localCms && (!"127.0.0.1".equals(environment.getProperty("server.address"))
                    || !"jdbc:h2:file:./data/cms-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,USER"
                            .equals(environment.getProperty("spring.datasource.url")))) {
                throw new IllegalStateException("local-cms requires loopback HTTP and the workspace H2 database");
            }
            if (!local && !environment.getProperty("afs.external.enabled", Boolean.class, false)) {
                throw new IllegalStateException("External CMS profiles are disabled; use local-cms or agent-sim");
            }
        });
        app.run(args);
    }

    @Bean
    WebMvcConfigurer consoleRoute() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController("/console", "/index.html");
            }
        };
    }
}
