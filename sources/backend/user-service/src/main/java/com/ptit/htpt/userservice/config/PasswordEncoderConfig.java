package com.ptit.htpt.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Phase 6 / Plan 01 (AUTH-01): Khai báo BCryptPasswordEncoder bean độc lập.
 *
 * Không dùng full Spring Security (@EnableWebSecurity) — chỉ cần spring-security-crypto standalone.
 * BCryptPasswordEncoder không trigger SecurityFilterChain, không intercept HTTP requests.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
