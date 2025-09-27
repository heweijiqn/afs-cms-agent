package com.afs.agent;

import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cms.agent.require-staff-login", havingValue = "true")
@EnableWebSecurity
class StaffSecurity {
    @Bean
    UserDetailsService staff(
            @Value("${cms.agent.operator-password:}") String operator,
            @Value("${cms.agent.reviewer-password:}") String reviewer) {
        if (operator.length() < 12 || reviewer.length() < 12 || operator.equals(reviewer)
                || operator.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || reviewer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Set distinct staff passwords of at least 12 characters and at most 72 UTF-8 bytes");
        }
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
                User.withUsername("operator").password("{bcrypt}" + encoder.encode(operator)).roles("OPERATOR").build(),
                User.withUsername("reviewer").password("{bcrypt}" + encoder.encode(reviewer)).roles("REVIEWER").build());
    }

    @Bean
    SecurityFilterChain staffRoutes(HttpSecurity http) throws Exception {
        return http
                .authorizeRequests(routes -> routes
                        .antMatchers(HttpMethod.GET, "/", "/index.html", "/console", "/console/**", "/cms/console/bootstrap").permitAll()
                        .antMatchers(HttpMethod.GET, "/cms/console/log-report", "/cms/console/archive-data",
                                "/cms/console/channel-credentials", "/cms/console/local-api", "/api/**").authenticated()
                        .antMatchers(HttpMethod.POST, "/cms/console/channel-credentials", "/cms/console/local-api", "/api/**").hasRole("OPERATOR")
                        .antMatchers(HttpMethod.POST, "/cms/agent/runs/*/review").hasRole("REVIEWER")
                        .antMatchers(HttpMethod.POST, "/cms/agent/**").hasRole("OPERATOR")
                        .antMatchers(HttpMethod.DELETE, "/cms/agent/chat/**").hasRole("OPERATOR")
                        .antMatchers(HttpMethod.GET, "/cms/agent/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers(this::isAgentApiMutation))
                .build();
    }

    private boolean isAgentApiMutation(HttpServletRequest request) {
        String path = request.getServletPath();
        if ("DELETE".equals(request.getMethod()) && path.startsWith("/cms/agent/chat/")) {
            return true;
        }
        return "POST".equals(request.getMethod()) && (path.startsWith("/cms/agent/")
                || path.equals("/cms/console/channel-credentials") || path.equals("/cms/console/local-api")
                || path.startsWith("/api/"))
                && request.getContentType() != null
                && "application/json".equalsIgnoreCase(request.getContentType().split(";", 2)[0]);
    }
}
