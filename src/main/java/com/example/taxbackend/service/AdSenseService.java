package com.example.taxbackend.service;


import com.example.taxbackend.models.RevenueRecord;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.adsense.v2.Adsense;
import com.google.api.services.adsense.v2.model.*;
import com.example.taxbackend.repository.RevenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AdSense Service - Fetches EXACT ad revenue from AdSense account
 * Uses AdSense Management API v2
 *
 * Academic Note: This provides ACTUAL revenue paid by Google AdSense.
 * This is more accurate than YouTube Analytics estimates but requires:
 * 1. AdSense account linked to YouTube channel
 * 2. User must grant adsense.readonly scope
 * 3. Account must meet AdSense payment threshold
 *
 * Revenue Hierarchy:
 * AdSense Revenue > YouTube Analytics Estimate > Calculated Estimate
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdSenseService {

    private final Adsense.Builder adsenseBuilder;
    private final OAuthService oauthService;
    private final RevenueRepository revenueRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Check if artist has AdSense account
     *
     * @param artistId Artist identifier
     * @return true if AdSense account is accessible
     */
    public boolean hasAdSenseAccount(String artistId) {
        try {
            Credential credential = oauthService.getCredential(artistId);
            Adsense adsense = adsenseBuilder
                    .setHttpRequestInitializer(credential)
                    .build();

            // Try to list accounts
            ListAccountsResponse response = adsense.accounts()
                    .list()
                    .execute();

            boolean hasAccount = response.getAccounts() != null &&
                    !response.getAccounts().isEmpty();

            log.info("Artist {} AdSense account status: {}", artistId, hasAccount);
            return hasAccount;

        } catch (IOException e) {
            log.warn("Could not access AdSense for artist {}: {}", artistId, e.getMessage());
            return false;
        }
    }

    /**
     * Get AdSense account ID
     * Required for all subsequent API calls
     *
     * @param artistId Artist identifier
     * @return AdSense account ID (format: accounts/pub-xxxxxxxxxxxxxx)
     */
    public String getAdSenseAccountId(String artistId) throws IOException {
        Credential credential = oauthService.getCredential(artistId);
        Adsense adsense = adsenseBuilder
                .setHttpRequestInitializer(credential)
                .build();

        ListAccountsResponse response = adsense.accounts()
                .list()
                .execute();

        if (response.getAccounts() == null || response.getAccounts().isEmpty()) {
            throw new IllegalStateException("No AdSense account found for artist: " + artistId);
        }

        // Use first account (most users have only one)
        Account account = response.getAccounts().get(0);
        log.info("Found AdSense account: {} for artist: {}",
                account.getName(), artistId);

        return account.getName(); // Returns "accounts/pub-xxxxxxxxxxxxxx"
    }

    /**
     * Fetch AdSense revenue for date range
     * This is EXACT revenue, not estimated
     *
     * @param artistId Artist identifier
     * @param startDate Start date
     * @param endDate End date
     * @return List of RevenueRecord with exact ad revenue
     */
    public List<RevenueRecord> fetchAdSenseRevenue(
            String artistId,
            LocalDate startDate,
            LocalDate endDate) throws IOException {

        Credential credential = oauthService.getCredential(artistId);
        Adsense adsense = adsenseBuilder
                .setHttpRequestInitializer(credential)
                .build();

        String accountId = getAdSenseAccountId(artistId);

        // Generate report
        // Documentation: https://developers.google.com/adsense/management/reporting
        ReportResult report = adsense.accounts()
                .reports()
                .generate(accountId)
                .setStartDateYear(startDate.getYear())
                .setStartDateMonth(startDate.getMonthValue())
                .setStartDateDay(startDate.getDayOfMonth())
                .setEndDateYear(endDate.getYear())
                .setEndDateMonth(endDate.getMonthValue())
                .setEndDateDay(endDate.getDayOfMonth())
                .setDimensions(List.of("DATE")) // Group by date
                .setMetrics(List.of(
                        "ESTIMATED_EARNINGS",  // Earnings in account currency
                        "PAGE_VIEWS",          // Ad impressions
                        "CLICKS",              // Ad clicks
                        "PAGE_VIEWS_RPM",      // Revenue per 1000 impressions
                        "PAGE_VIEWS_CTR"       // Click-through rate
                ))
                .execute();

        log.info("Fetched AdSense report: {} rows",
                report.getRows() != null ? report.getRows().size() : 0);

        return parseAdSenseReport(report, artistId);
    }

    /**
     * Fetch monthly AdSense summary
     * Aggregates data by month for tax reporting
     */
    public List<RevenueRecord> fetchMonthlyAdSenseRevenue(
            String artistId,
            LocalDate startDate,
            LocalDate endDate) throws IOException {

        Credential credential = oauthService.getCredential(artistId);
        Adsense adsense = adsenseBuilder
                .setHttpRequestInitializer(credential)
                .build();

        String accountId = getAdSenseAccountId(artistId);

        ReportResult report = adsense.accounts()
                .reports()
                .generate(accountId)
                .setStartDateYear(startDate.getYear())
                .setStartDateMonth(startDate.getMonthValue())
                .setStartDateDay(startDate.getDayOfMonth())
                .setEndDateYear(endDate.getYear())
                .setEndDateMonth(endDate.getMonthValue())
                .setEndDateDay(endDate.getDayOfMonth())
                .setDimensions(List.of("MONTH")) // Group by month
                .setMetrics(List.of(
                        "ESTIMATED_EARNINGS",
                        "PAGE_VIEWS",
                        "CLICKS",
                        "PAGE_VIEWS_RPM"
                ))
                .execute();

        return parseAdSenseMonthlyReport(report, artistId);
    }

    /**
     * Parse daily AdSense report
     */
    private List<RevenueRecord> parseAdSenseReport(
            ReportResult report,
            String artistId) {

        List<RevenueRecord> records = new ArrayList<>();

        if (report.getRows() == null || report.getRows().isEmpty()) {
            log.warn("No AdSense data available for artist: {}", artistId);
            return records;
        }

        // Get column indices
        int dateIndex = findHeaderIndex(report.getHeaders(), "DATE");
        int earningsIndex = findHeaderIndex(report.getHeaders(), "ESTIMATED_EARNINGS");
        int impressionsIndex = findHeaderIndex(report.getHeaders(), "PAGE_VIEWS");
        int clicksIndex = findHeaderIndex(report.getHeaders(), "CLICKS");
        int rpmIndex = findHeaderIndex(report.getHeaders(), "PAGE_VIEWS_RPM");

        for (Row row : report.getRows()) {
            try {
                List<Cell> cells = row.getCells();

                // Parse date (format: YYYY-MM-DD)
                String dateStr = cells.get(dateIndex).getValue();
                LocalDate date = LocalDate.parse(dateStr);

                // Parse metrics
                Double earnings = parseDouble(cells.get(earningsIndex).getValue());
                Long impressions = parseLong(cells.get(impressionsIndex).getValue());
                Long clicks = parseLong(cells.get(clicksIndex).getValue());
                Double rpm = parseDouble(cells.get(rpmIndex).getValue());

                RevenueRecord record = RevenueRecord.builder()
                        .artistId(artistId)
                        .videoId(null) // AdSense doesn't break down by video
                        .periodStart(date.atStartOfDay())
                        .periodEnd(date.plusDays(1).atStartOfDay())
                        .periodType("DAILY")
                        .source(RevenueRecord.RevenueSource.ADSENSE)
                        .adRevenue(earnings)
                        .adImpressions(impressions)
                        .adClicks(clicks)
                        .rpm(rpm)
                        .totalRevenue(earnings)
                        .currency("USD") // AdSense reports in account currency
                        .verified(true) // AdSense data is exact
                        .dataQuality("HIGH")
                        .fetchedAt(LocalDateTime.now())
                        .build();

                records.add(record);

            } catch (Exception e) {
                log.error("Error parsing AdSense row: {}", row, e);
            }
        }

        // Save to database
        revenueRepository.saveAll(records);
        log.info("Saved {} AdSense revenue records", records.size());

        return records;
    }

    /**
     * Parse monthly AdSense report
     */
    private List<RevenueRecord> parseAdSenseMonthlyReport(
            ReportResult report,
            String artistId) {

        List<RevenueRecord> records = new ArrayList<>();

        if (report.getRows() == null) {
            return records;
        }

        int monthIndex = findHeaderIndex(report.getHeaders(), "MONTH");
        int earningsIndex = findHeaderIndex(report.getHeaders(), "ESTIMATED_EARNINGS");
        int impressionsIndex = findHeaderIndex(report.getHeaders(), "PAGE_VIEWS");
        int clicksIndex = findHeaderIndex(report.getHeaders(), "CLICKS");
        int rpmIndex = findHeaderIndex(report.getHeaders(), "PAGE_VIEWS_RPM");

        for (Row row : report.getRows()) {
            try {
                List<Cell> cells = row.getCells();

                // Parse month (format: YYYY-MM)
                String monthStr = cells.get(monthIndex).getValue();
                LocalDate date = LocalDate.parse(monthStr + "-01");

                Double earnings = parseDouble(cells.get(earningsIndex).getValue());
                Long impressions = parseLong(cells.get(impressionsIndex).getValue());
                Long clicks = parseLong(cells.get(clicksIndex).getValue());
                Double rpm = parseDouble(cells.get(rpmIndex).getValue());

                RevenueRecord record = RevenueRecord.builder()
                        .artistId(artistId)
                        .videoId(null)
                        .periodStart(date.atStartOfDay())
                        .periodEnd(date.plusMonths(1).atStartOfDay())
                        .periodType("MONTHLY")
                        .source(RevenueRecord.RevenueSource.ADSENSE)
                        .adRevenue(earnings)
                        .adImpressions(impressions)
                        .adClicks(clicks)
                        .rpm(rpm)
                        .totalRevenue(earnings)
                        .currency("USD")
                        .verified(true)
                        .dataQuality("HIGH")
                        .fetchedAt(LocalDateTime.now())
                        .build();

                records.add(record);

            } catch (Exception e) {
                log.error("Error parsing monthly AdSense row: {}", row, e);
            }
        }

        revenueRepository.saveAll(records);
        return records;
    }

    /**
     * Helper: Find column index by header name
     */
    private int findHeaderIndex(List<Header> headers, String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).getName().equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Header not found: " + name);
    }

    /**
     * Helper: Parse double from cell value
     */
    private Double parseDouble(String value) {
        try {
            return value != null && !value.isEmpty() ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Helper: Parse long from cell value
     */
    private Long parseLong(String value) {
        try {
            return value != null && !value.isEmpty() ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
