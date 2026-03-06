package com.example.taxbackend.config;

import com.example.taxbackend.models.TaxConfiguration;
import com.example.taxbackend.repository.TaxConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Tax Configuration Initializer
 *
 * Academic Note: Initializes default tax configuration for Zimbabwe
 * Digital Services Tax on application startup if not already configured
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class TaxConfigInitializer {

    private final TaxConfigurationRepository taxConfigRepository;

    @Bean
    public CommandLineRunner initializeTaxConfig() {
        return args -> {
            // Check if configuration already exists
            if (taxConfigRepository.findByActiveTrue().isPresent()) {
                log.info("Tax configuration already exists, skipping initialization");
                return;
            }

            log.info("Initializing default tax configuration...");

            // Create Zimbabwe Digital Services Tax configuration
            TaxConfiguration config = TaxConfiguration.builder()
                    .country("Zimbabwe")
                    .taxType("DIGITAL_SERVICES_TAX")
                    // Tax rates
                    .standardRate(0.10)         // 10% standard rate
                    .reducedRate(0.05)          // 5% for small earners (not currently used)
                    .thresholdAmount(100.0)     // Minimum $100 revenue to be taxed
                    // Deductions
                    .allowDeductions(true)
                    .standardDeduction(0.0)     // No standard deduction currently
                    .deductionPercentage(0.0)   // No percentage deduction currently
                    .allowedDeductionTypes(Arrays.asList(
                            "BUSINESS_EXPENSES",
                            "EQUIPMENT_COSTS",
                            "PRODUCTION_COSTS"
                    ))
                    // Payment terms
                    .paymentDueDays(30)         // 30 days after period end
                    .quarterlyFiling(false)     // Monthly filing
                    .monthlyFiling(true)
                    // Penalties
                    .latePenaltyRate(0.02)      // 2% per month late penalty
                    .interestRate(0.10)         // 10% annual interest on overdue
                    // Effective dates
                    .effectiveFrom(LocalDateTime.of(2024, 1, 1, 0, 0))
                    .effectiveTo(LocalDateTime.of(2025, 12, 31, 23, 59))
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            taxConfigRepository.save(config);

            log.info("=== Tax Configuration Initialized ===");
            log.info("Country: {}", config.getCountry());
            log.info("Tax Type: {}", config.getTaxType());
            log.info("Standard Rate: {}%", config.getStandardRate() * 100);
            log.info("Threshold: ${}", config.getThresholdAmount());
            log.info("Payment Due: {} days", config.getPaymentDueDays());
            log.info("====================================");
        };
    }
}

// ============================================================================
// Application Properties for Tax Configuration
// ============================================================================

/**
 * Add these properties to application.yml for customization:
 *
 * tax:
 *   zimbabwe:
 *     digital-services:
 *       standard-rate: 0.10
 *       threshold: 100.0
 *       payment-due-days: 30
 *       allow-deductions: true
 *       late-penalty-rate: 0.02
 *       interest-rate: 0.10
 *
 * scheduler:
 *   enabled: true
 *   monthly-calculation:
 *     cron: "0 0 2 1 * ?"  # 1st of month at 2 AM
 *   overdue-check:
 *     cron: "0 0 3 * * ?"  # Daily at 3 AM
 */
