package com.example.taxbackend.auditing;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {


    public AuditorAwareImpl(UserDetailsService userDetailsService) {
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();
        System.out.println(authentication.getPrincipal());
        return Optional.ofNullable( authentication.getName());

    }
}
