package com.iceibank.agencia.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import io.jsonwebtoken.security.Keys;

@Configuration
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String segredo;

    @Bean
    public SecretKey jwtSecretKey() {
        return Keys.hmacShaKeyFor(
                segredo.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {

        return NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS384)
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(session
                        -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                )
                )
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login")
                .permitAll()
                .anyRequest()
                .authenticated()
                )
                .oauth2ResourceServer(oauth2
                        -> oauth2.jwt(jwt -> {
                })
                );

        return http.build();
    }
}
