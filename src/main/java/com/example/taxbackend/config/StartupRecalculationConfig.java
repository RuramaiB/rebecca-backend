package com.example.taxbackend.config;

import com.example.taxbackend.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class StartupRecalculationConfig {

    private final TaxService taxService;

    @Value("${tax.recalculate-on-startup:true}")
    private boolean recalculateOnStartup;

    @Bean
    public CommandLineRunner startupTaxRecalculationRunner() {
        return args -> {
            if (!recalculateOnStartup) {
                log.info("Startup tax recalculation disabled by configuration.");
                return;
            }
            long started = System.currentTimeMillis();
            log.info("Starting startup tax recalculation for all onboarded artists...");
            taxService.recalculateAllArtistsFromOnboarding();
            log.info("Startup tax recalculation complete in {} ms", (System.currentTimeMillis() - started));
        };
    }
}
