package com.example.taxbackend.auditing;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
@EnableMongoAuditing(auditorAwareRef = "auditorProvider")
@RequiredArgsConstructor
public class AuditConfig {
    private final UserDetailsService userDetailsService;

    @Bean
    public AuditorAware<String> auditorProvider(){ return  new AuditorAwareImpl(userDetailsService);}
}
