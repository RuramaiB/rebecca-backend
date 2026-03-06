package com.example.taxbackend.service;

import com.example.taxbackend.dtos.TaxCalculationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tax Scheduler - Automated tax calculation and compliance checking
 *
 * Academic Note: This demonstrates how a production system would
 * automate tax calculations without manual intervention.
 *
 * Schedules:
 * 1. Monthly tax calculation (1st day of month at 2 AM)
 * 2. Daily overdue payment check (every day at 3 AM)
 * 3. Weekly compliance report (every Monday at 9 AM)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaxScheduler {

    private final TaxService taxService;

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Monthly tax calculation
     * Runs on the 1st day of each month at 2:00 AM
     * Calculates tax for the previous month
     *
     * Cron: 0 0 2 1 * ? (second, minute, hour, day, month, day-of-week)
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void calculateMonthlyTax() {
        log.info("=== SCHEDULED: Monthly Tax Calculation Started ===");
        LocalDateTime now = LocalDateTime.now();

        try {
            // Calculate for previous month
            YearMonth previousMonth = YearMonth.now().minusMonths(1);
            String period = previousMonth.format(PERIOD_FORMATTER);

            log.info("Calculating tax for period: {}", period);

            // Calculate for all authorized artists
            List<TaxCalculationResult> results = taxService.calculateTaxForAllArtists(period);

            // Log summary
            long successful = results.stream().filter(TaxCalculationResult::isSuccess).count();
            long failed = results.size() - successful;

            log.info("Tax calculation completed: {} successful, {} failed", successful, failed);

            // Log failures
            results.stream()
                    .filter(r -> !r.isSuccess())
                    .forEach(r -> log.warn("Failed for artist {}: {}",
                            r.getArtistId(), r.getMessage()));

            log.info("=== SCHEDULED: Monthly Tax Calculation Completed ===");

        } catch (Exception e) {
            log.error("Error in scheduled monthly tax calculation", e);
        }
    }

    /**
     * Daily overdue payment check
     * Runs every day at 3:00 AM
     * Marks pending payments as overdue if past due date
     *
     * Cron: 0 0 3 * * ?
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void checkOverduePayments() {
        log.info("=== SCHEDULED: Overdue Payment Check Started ===");

        try {
            taxService.markOverduePayments();
            log.info("=== SCHEDULED: Overdue Payment Check Completed ===");

        } catch (Exception e) {
            log.error("Error in scheduled overdue payment check", e);
        }
    }

    /**
     * Weekly compliance report
     * Runs every Monday at 9:00 AM
     * Logs compliance summary for monitoring
     *
     * Cron: 0 0 9 ? * MON
     */
    @Scheduled(cron = "0 0 9 ? * MON")
    public void generateWeeklyComplianceReport() {
        log.info("=== SCHEDULED: Weekly Compliance Report ===");

        try {
            // This would typically send email reports to admins
            // For prototype, we just log
            log.info("Compliance report generation scheduled");
            log.info("In production: This would email detailed reports to tax authorities");

        } catch (Exception e) {
            log.error("Error generating weekly compliance report", e);
        }
    }


    // @Scheduled(cron = "0 */5 * * * ?")
    public void testScheduler() {
        log.info("=== TEST SCHEDULER: Running at {} ===", LocalDateTime.now());

        // For academic demonstration:
        // Calculate tax for current month (even though it's not complete)
        YearMonth currentMonth = YearMonth.now();
        String period = currentMonth.format(PERIOD_FORMATTER);

        log.info("Test calculation for period: {}", period);

        try {
            List<TaxCalculationResult> results = taxService.calculateTaxForAllArtists(period);
            log.info("Test calculation completed: {} results", results.size());

        } catch (Exception e) {
            log.error("Test scheduler error", e);
        }
    }

    /**
     * Manual trigger for immediate tax calculation
     * Can be called via JMX or admin endpoint
     *
     * @param period Period to calculate (format: "YYYY-MM")
     */
    public void triggerManualCalculation(String period) {
        log.info("=== MANUAL TRIGGER: Tax Calculation for {} ===", period);

        try {
            List<TaxCalculationResult> results = taxService.calculateTaxForAllArtists(period);

            long successful = results.stream().filter(TaxCalculationResult::isSuccess).count();
            long failed = results.size() - successful;

            log.info("Manual calculation completed: {} successful, {} failed",
                    successful, failed);

        } catch (Exception e) {
            log.error("Error in manual tax calculation", e);
        }
    }
}
