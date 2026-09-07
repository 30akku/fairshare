package com.anshi.fairshare.config;

import com.anshi.fairshare.security.FirebaseAuthenticationFilter;
import com.anshi.fairshare.security.FixedWindowRateLimitFilter;
import com.anshi.fairshare.security.RecurrenceCatchupFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, FirebaseAuthenticationFilter authFilter,
                                            FixedWindowRateLimitFilter rateLimitFilter,
                                            RecurrenceCatchupFilter recurrenceCatchupFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, FirebaseAuthenticationFilter.class)
                .addFilterAfter(recurrenceCatchupFilter, FixedWindowRateLimitFilter.class)
                .build();
    }
}
