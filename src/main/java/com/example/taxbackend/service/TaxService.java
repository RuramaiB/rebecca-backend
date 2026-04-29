package com.example.taxbackend.service;

import com.example.taxbackend.dtos.TaxCalculationResult;
import com.example.taxbackend.dtos.TaxRecordDTO;
import com.example.taxbackend.models.*;
import com.example.taxbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Tax Service - Core tax calculation engine
 *
 * Academic Note: This service calculates taxes based on fetched revenue
 * and Zimbabwean digital services tax rules (simplified for prototype)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaxService {

    private final TaxRecordRepository taxRecordRepository;
    private final TaxConfigurationRepository taxConfigRepository;
    private final RevenueRepository revenueRepository;
    private final ComplianceStatusRepository complianceRepository;
    private final ArtistRepository artistRepository;
    private final VideoMetadataRepository videoMetadataRepository;

    // Default tax configuration (Zimbabwe Digital Services Tax)
    private static final double DEFAULT_TAX_RATE = 0.10; // 10%
    private static final double DEFAULT_OPERATION_TAX_RATE = 0.10; // Default to standard rate
    private static final double MONTHLY_CONTENT_ACCRUAL = 0.0; // Reset to 0
    private static final double THRESHOLD_AMOUNT = 0.0; // Reset to 0 (tax all revenue)
    private static final double OPERATION_TAX_THRESHOLD = 0.0; // Reset to 0
    private static final double DEFAULT_TAX_PER_MILLION = 150.0; // $150 per 1,000,000 views
    private static final int PAYMENT_DUE_DAYS = 30; // 30 days after period end
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Calculate tax for specific artist and period
     * Main entry point for tax calculation
     *
     * @param artistId Artist ID
     * @param period   Period in format "YYYY-MM"
     * @return Tax calculation result
     */
    @Transactional
    public TaxCalculationResult calculateTax(String artistId, String period) {
        log.info("Calculating tax for artist {} period {}", artistId, period);

        // Check if already calculated
        Optional<TaxRecord> existing = taxRecordRepository.findByArtistIdAndPeriod(artistId, period);
        if (existing.isPresent()) {
            log.info("Tax already calculated for period {}, returning existing", period);
            return toCalculationResult(existing.get());
        }

        // Get tax configuration
        TaxConfiguration config = getActiveTaxConfig();

        // Parse period
        YearMonth yearMonth = YearMonth.parse(period);
        LocalDateTime periodStart = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime periodEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        // Fetch revenue for period
        List<RevenueRecord> revenues = revenueRepository.findByArtistIdAndPeriodStartBetween(
                artistId, periodStart, periodEnd);

        if (revenues.isEmpty()) {
            log.warn("No revenue data found for artist {} period {}", artistId, period);

            boolean hasActiveContent = hasActiveContentByPeriod(artistId, periodEnd);
            if (hasActiveContent) {
                log.info("Artist {} has active content but no revenue in period {}, applying content accrual tax",
                        artistId, period);
                return applyOperationTax(artistId, period, 0.0, periodStart, periodEnd, config);
            }

            return TaxCalculationResult.builder()
                    .artistId(artistId)
                    .period(period)
                    .success(false)
                    .message("No revenue data available for period")
                    .build();
        }

        // Calculate revenue totals
        double grossRevenue = revenues.stream()
                .mapToDouble(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : 0.0)
                .sum();

        double youtubeRevenue = revenues.stream()
                .filter(r -> r.getSource() == RevenueRecord.RevenueSource.YOUTUBE_ANALYTICS)
                .mapToDouble(r -> r.getEstimatedRevenue() != null ? r.getEstimatedRevenue() : 0.0)
                .sum();

        double adsenseRevenue = revenues.stream()
                .filter(r -> r.getSource() == RevenueRecord.RevenueSource.ADSENSE)
                .mapToDouble(r -> r.getAdRevenue() != null ? r.getAdRevenue() : 0.0)
                .sum();

        boolean hasActiveContent = hasActiveContentByPeriod(artistId, periodEnd);

        // Apply operation tax for accounts with videos but revenue below operation tax
        // threshold
        if (hasActiveContent && grossRevenue < config.getOperationTaxThreshold()) {
            log.info("Artist {} has active content but revenue ${} below operation tax threshold ${}, applying content accrual tax",
                    artistId, grossRevenue, config.getOperationTaxThreshold());
            return applyOperationTax(artistId, period, grossRevenue, periodStart, periodEnd, config);
        }

        // Check standard tax threshold
        if (grossRevenue < config.getThresholdAmount()) {
            log.info("Revenue ${} below threshold ${}, no tax due",
                    grossRevenue, config.getThresholdAmount());

            TaxRecord record = createZeroTaxRecord(
                    artistId, period, grossRevenue, youtubeRevenue,
                    adsenseRevenue, periodStart, periodEnd, config);

            taxRecordRepository.save(record);
            updateComplianceStatus(artistId);

            return toCalculationResult(record);
        }

        // Count videos and fetch them to sum views
        List<Video> periodVideos = videoMetadataRepository.findByArtistIdAndPublishedAtBetween(artistId, periodStart,
                periodEnd);
        int videoCount = periodVideos.size();
        int shotCount = (int) periodVideos.stream().filter(v -> isShort(v.getDuration())).count();
        long totalViews = periodVideos.stream().mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L).sum();

        // Calculate view-based tax
        double ratePerMillion = config.getTaxRatePerMillion() != null ? config.getTaxRatePerMillion() : DEFAULT_TAX_PER_MILLION;
        double taxAmount = (totalViews / 1000.0) * (ratePerMillion / 1000.0);
        double netRevenue = grossRevenue - taxAmount;
        double taxableIncome = grossRevenue; // Simplified

        // Create tax record
        TaxRecord record = TaxRecord.builder()
                .videoCount(videoCount)
                .shotCount(shotCount)
                .artistId(artistId)
                .grossRevenue(grossRevenue)
                .youtubeRevenue(youtubeRevenue)
                .adsenseRevenue(adsenseRevenue)
                .taxableIncome(taxableIncome)
                .taxRate(config.getStandardRate())
                .taxAmount(taxAmount)
                .netRevenue(netRevenue)
                .deductions(0.0)
                .period(period)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .taxYear(String.valueOf(yearMonth.getYear()))
                .taxQuarter(getQuarter(yearMonth.getMonthValue()))
                .calculatedAt(LocalDateTime.now())
                .calculatedBy("SYSTEM")
                .calculationMethod("AUTOMATIC")
                .paymentStatus(TaxRecord.PaymentStatus.PENDING)
                .dueDate(periodEnd.plusDays(config.getPaymentDueDays()))
                .filed(false)
                .notes(String.format(
                        "Digital Service Tax (View-based): $%.4f (Rate: $%.2f per million views, Total views: %d)",
                        taxAmount, ratePerMillion, totalViews))
                .build();

        record = taxRecordRepository.save(record);
        log.info("Standard tax calculated: ${} on revenue ${}",
                taxAmount, grossRevenue);

        // Update compliance status
        updateComplianceStatus(artistId);

        return toCalculationResult(record);
    }

    /**
     * Internal method to apply operation tax for accounts with videos but low/no
     * revenue
     * This is a nominal tax to ensure compliance tracking for all active content
     * creators
     *
     * @param artistId     Artist ID
     * @param period       Period in format "YYYY-MM"
     * @param grossRevenue Gross revenue (could be zero)
     * @param periodStart  Period start datetime
     * @param periodEnd    Period end datetime
     * @param config       Tax configuration
     * @return Tax calculation result
     */
    @Transactional
    public TaxCalculationResult applyOperationTax(String artistId, String period,
            double grossRevenue,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            TaxConfiguration config) {
        log.info("Applying operation tax for artist {} period {}", artistId, period);

        // Check if operation tax already calculated
        Optional<TaxRecord> existing = taxRecordRepository.findByArtistIdAndPeriodAndTaxType(
                artistId, period, TaxRecord.TaxType.OPERATION_TAX);
        if (existing.isPresent()) {
            log.info("Operation tax already calculated for period {}, returning existing", period);
            return toCalculationResult(existing.get());
        }

        YearMonth yearMonth = YearMonth.parse(period);
        
        // Count videos and sum views
        List<Video> periodVideos = videoMetadataRepository.findByArtistIdAndPublishedAtBetween(artistId, periodStart,
                periodEnd);
        int videoCount = periodVideos.size();
        int shotCount = (int) periodVideos.stream().filter(v -> isShort(v.getDuration())).count();
        long totalViews = periodVideos.stream().mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L).sum();

        double ratePerMillion = config.getTaxRatePerMillion() != null ? config.getTaxRatePerMillion() : DEFAULT_TAX_PER_MILLION;
        double operationTaxAmount = (totalViews / 1000.0) * (ratePerMillion / 1000.0);
        double netRevenue = Math.max(0, grossRevenue - operationTaxAmount);

        // Create operation tax record
        TaxRecord record = TaxRecord.builder()
                .videoCount(videoCount)
                .shotCount(shotCount)
                .artistId(artistId)
                .grossRevenue(grossRevenue)
                .taxableIncome(grossRevenue)
                .taxRate(config.getStandardRate())
                .taxAmount(operationTaxAmount)
                .netRevenue(netRevenue)
                .deductions(0.0)
                .period(period)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .taxYear(String.valueOf(yearMonth.getYear()))
                .taxQuarter(getQuarter(yearMonth.getMonthValue()))
                .calculatedAt(LocalDateTime.now())
                .calculatedBy("SYSTEM")
                .calculationMethod("OPERATION_TAX")
                .taxType(TaxRecord.TaxType.OPERATION_TAX.toString())
                .paymentStatus(TaxRecord.PaymentStatus.PENDING)
                .dueDate(periodEnd.plusDays(config.getPaymentDueDays()))
                .filed(false)
                .notes(String.format(
                        "Monthly Digital Service Tax (View-based): $%.4f (Rate: $%.2f per million views, Total views: %d)",
                        operationTaxAmount, ratePerMillion, totalViews))
                // .metadata("{\"video_count\": " + videoCount + ", \"tax_type\":
                // \"operation_tax\"}")
                .build();

        record = taxRecordRepository.save(record);
        log.info("Low-revenue tax calculated: ${} on revenue ${}",
                operationTaxAmount, grossRevenue);

        // Update compliance status
        updateComplianceStatus(artistId);

        return toCalculationResult(record);
    }

    /**
     * Calculate tax for all authorized artists for given period
     * Includes operation tax for accounts with videos but low revenue
     *
     * @param period Period in format "YYYY-MM"
     * @return List of calculation results
     */
    @Transactional
    public List<TaxCalculationResult> calculateTaxForAllArtists(String period) {
        log.info("Calculating tax for all artists for period {}", period);

        List<Artist> artists = artistRepository.findByAnalyticsAuthorizedTrue();
        log.info("Found {} authorized artists", artists.size());

        return artists.stream()
                .map(artist -> {
                    try {
                        return calculateTax(artist.getId(), period);
                    } catch (Exception e) {
                        log.error("Error calculating tax for artist {}: {}",
                                artist.getId(), e.getMessage());
                        return TaxCalculationResult.builder()
                                .artistId(artist.getId())
                                .period(period)
                                .success(false)
                                .message("Calculation failed: " + e.getMessage())
                                .build();
                    }
                })
                .toList();
    }

    @Transactional
    public TaxCalculationResult recalculateTax(String artistId, String period) {
        taxRecordRepository.deleteByArtistIdAndPeriod(artistId, period);
        return calculateTax(artistId, period);
    }

    @Transactional
    public List<TaxCalculationResult> recalculateArtistFromOnboarding(String artistId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + artistId));

        LocalDateTime authorizedAt = artist.getAuthorizedAt() != null ? artist.getAuthorizedAt() : LocalDateTime.now();
        YearMonth startMonth = YearMonth.from(authorizedAt);
        YearMonth currentMonth = YearMonth.now();

        List<TaxCalculationResult> results = new java.util.ArrayList<>();
        for (YearMonth month = startMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            String period = month.format(PERIOD_FORMATTER);
            try {
                results.add(recalculateTax(artistId, period));
            } catch (Exception ex) {
                results.add(TaxCalculationResult.builder()
                        .artistId(artistId)
                        .period(period)
                        .success(false)
                        .message("Recalculation failed: " + ex.getMessage())
                        .build());
            }
        }

        updateComplianceStatus(artistId);
        return results;
    }

    @Transactional
    public void recalculateAllArtistsFromOnboarding() {
        List<Artist> artists = artistRepository.findAllByRole(com.example.taxbackend.user.Role.ARTIST);
        log.info("Startup tax recalculation started for {} artists", artists.size());

        artists.parallelStream().forEach(artist -> {
            try {
                List<TaxCalculationResult> artistResults = recalculateArtistFromOnboarding(artist.getId());
                long failures = artistResults.stream().filter(r -> !r.isSuccess()).count();
                log.info("Recalculated artist {} across {} periods (failures: {})",
                        artist.getId(), artistResults.size(), failures);
            } catch (Exception ex) {
                log.error("Failed startup recalculation for artist {}: {}", artist.getId(), ex.getMessage());
            }
        });
    }

    /**
     * Calculate tax for all artists with videos in period (including
     * non-authorized)
     * This ensures operation tax is applied to all active content creators
     *
     * @param period Period in format "YYYY-MM"
     * @return List of calculation results
     */
    @Transactional
    public List<TaxCalculationResult> calculateTaxForAllVideoCreators(String period) {
        log.info("Calculating tax for all video creators for period {}", period);

        YearMonth yearMonth = YearMonth.parse(period);
        LocalDateTime periodStart = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime periodEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        // Get all artists with videos in the period
        List<String> artistIdsWithVideos = videoMetadataRepository.findDistinctArtistIdsByUploadDateBetween(
                periodStart, periodEnd);

        log.info("Found {} artists with videos in period {}", artistIdsWithVideos.size(), period);

        return artistIdsWithVideos.stream()
                .map(artistId -> {
                    try {
                        return calculateTax(artistId, period);
                    } catch (Exception e) {
                        log.error("Error calculating tax for artist {}: {}",
                                artistId, e.getMessage());
                        return TaxCalculationResult.builder()
                                .artistId(artistId)
                                .period(period)
                                .success(false)
                                .message("Calculation failed: " + e.getMessage())
                                .build();
                    }
                })
                .toList();
    }

    /**
     * Get tax records for artist
     *
     * @param artistId Artist ID
     * @return List of tax records
     */
    public List<TaxRecord> getTaxRecords(String artistId) {
        return taxRecordRepository.findByArtistIdOrderByPeriodStartDesc(artistId);
    }

    /**
     * Get operation tax records for artist
     *
     * @param artistId Artist ID
     * @return List of operation tax records
     */
    public List<TaxRecord> getOperationTaxRecords(String artistId) {
        return taxRecordRepository.findByArtistIdAndTaxTypeOrderByPeriodStartDesc(
                artistId, TaxRecord.TaxType.OPERATION_TAX);
    }

    public List<TaxRecord> getTaxRecords() {
        List<String> adminIds = artistRepository.findAllByRole(com.example.taxbackend.user.Role.ADMIN)
                .stream().map(Artist::getId).toList();
        return taxRecordRepository.findAll().stream()
                .filter(r -> !adminIds.contains(r.getArtistId()))
                .toList();
    }

    /**
     * Get tax record for specific period
     *
     * @param artistId Artist ID
     * @param period   Period
     * @return Tax record if exists
     */
    public Optional<TaxRecord> getTaxRecord(String artistId, String period) {
        return taxRecordRepository.findByArtistIdAndPeriod(artistId, period);
    }

    /**
     * Get outstanding tax records for artist
     *
     * @param artistId Artist ID
     * @return List of unpaid tax records
     */
    public List<TaxRecord> getOutstandingTax(String artistId) {
        return taxRecordRepository.findOutstandingTaxRecords(artistId);
    }

    /**
     * Mark tax as paid
     *
     * @param taxRecordId      Tax record ID
     * @param paymentReference Payment reference
     * @param paymentMethod    Payment method
     */
    @Transactional
    public void markAsPaid(String taxRecordId, String paymentReference, String paymentMethod) {
        TaxRecord record = taxRecordRepository.findById(taxRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Tax record not found"));

        record.setPaymentStatus(TaxRecord.PaymentStatus.PAID);
        record.setPaidDate(LocalDateTime.now());
        record.setPaymentReference(paymentReference);
        record.setPaymentMethod(paymentMethod);
        record.setLastModified(LocalDateTime.now());

        taxRecordRepository.save(record);
        log.info("Tax record {} marked as paid", taxRecordId);

        // Update compliance
        updateComplianceStatus(record.getArtistId());
    }

    /**
     * Check and mark overdue payments
     * Should be run daily by scheduler
     */
    @Transactional
    public void markOverduePayments() {
        log.info("Checking for overdue payments");

        LocalDateTime now = LocalDateTime.now();
        List<TaxRecord> overdue = taxRecordRepository.findByPaymentStatusAndDueDateBefore(
                TaxRecord.PaymentStatus.PENDING, now);

        log.info("Found {} overdue payments", overdue.size());

        for (TaxRecord record : overdue) {
            record.setPaymentStatus(TaxRecord.PaymentStatus.OVERDUE);
            record.setLastModified(now);
            taxRecordRepository.save(record);

            // Update compliance
            updateComplianceStatus(record.getArtistId());
        }
    }

    /**
     * Get active tax configuration
     * Falls back to defaults if not configured
     */
    private TaxConfiguration getActiveTaxConfig() {
        return taxConfigRepository.findByActiveTrue()
                .orElseGet(() -> {
                    log.warn("No active tax configuration, using defaults");
                    return TaxConfiguration.builder()
                            .standardRate(DEFAULT_TAX_RATE)
                            .operationTaxRate(DEFAULT_OPERATION_TAX_RATE)
                            .thresholdAmount(THRESHOLD_AMOUNT)
                            .operationTaxThreshold(OPERATION_TAX_THRESHOLD)
                            .paymentDueDays(PAYMENT_DUE_DAYS)
                            .allowDeductions(true)
                            .standardDeduction(0.0)
                            .deductionPercentage(0.0)
                            .build();
                });
    }

    /**
     * Check if artist has videos in the specified period
     */
    private boolean hasVideosInPeriod(String artistId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Assuming you have a VideoMetadataRepository with a method to check for videos
        long videoCount = countVideosInPeriod(artistId, periodStart, periodEnd);
        return videoCount > 0;
    }

    private boolean hasActiveContentByPeriod(String artistId, LocalDateTime periodEnd) {
        return getActiveContentCountByPeriod(artistId, periodEnd) > 0;
    }

    private long getActiveContentCountByPeriod(String artistId, LocalDateTime periodEnd) {
        return videoMetadataRepository.countByArtistIdAndPublishedAtLessThanEqual(artistId, periodEnd);
    }

    /**
     * Count videos for artist in the specified period
     */
    private long countVideosInPeriod(String artistId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Assuming you have a VideoMetadataRepository
        // You'll need to create this repository if it doesn't exist
        return videoMetadataRepository.countByArtistIdAndPublishedAt(artistId, periodStart, periodEnd);
    }

    /**
     * Calculate deductions
     * Currently simplified - can be extended for production
     */
    private double calculateDeductions(double grossRevenue, TaxConfiguration config) {
        if (!config.isAllowDeductions()) {
            return 0.0;
        }

        double standardDeduction = config.getStandardDeduction() != null
                ? config.getStandardDeduction()
                : 0.0;
        double percentageDeduction = config.getDeductionPercentage() != null
                ? grossRevenue * config.getDeductionPercentage()
                : 0.0;

        return standardDeduction + percentageDeduction;
    }

    /**
     * Get quarter from month
     */
    private String getQuarter(int month) {
        if (month <= 3)
            return "Q1";
        if (month <= 6)
            return "Q2";
        if (month <= 9)
            return "Q3";
        return "Q4";
    }

    /**
     * Create tax record with zero tax (below threshold)
     */
    private TaxRecord createZeroTaxRecord(
            String artistId, String period, double grossRevenue,
            double youtubeRevenue, double adsenseRevenue,
            LocalDateTime periodStart, LocalDateTime periodEnd,
            TaxConfiguration config) {

        YearMonth yearMonth = YearMonth.parse(period);

        // Count videos and shots in period
        List<Video> periodVideos = videoMetadataRepository.findByArtistIdAndPublishedAtBetween(artistId, periodStart,
                periodEnd);
        int videoCount = periodVideos.size();
        int shotCount = (int) periodVideos.stream().filter(v -> isShort(v.getDuration())).count();
        // No content accrual

        double taxAmount = grossRevenue * config.getStandardRate();
        return TaxRecord.builder()
                .videoCount(videoCount)
                .shotCount(shotCount)
                .artistId(artistId)
                .grossRevenue(grossRevenue)
                .youtubeRevenue(youtubeRevenue)
                .adsenseRevenue(adsenseRevenue)
                .taxableIncome(grossRevenue)
                .taxRate(config.getStandardRate())
                .taxAmount(taxAmount)
                .netRevenue(grossRevenue - taxAmount)
                .deductions(0.0)
                .period(period)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .taxYear(String.valueOf(yearMonth.getYear()))
                .taxQuarter(getQuarter(yearMonth.getMonthValue()))
                .calculatedAt(LocalDateTime.now())
                .calculatedBy("SYSTEM")
                .calculationMethod("AUTOMATIC")
                .taxType(TaxRecord.TaxType.STANDARD.toString())
                .paymentStatus(TaxRecord.PaymentStatus.PENDING)
                .filed(false)
                .notes(String.format(
                        "Digital Service Tax (View-based): $%.4f",
                        taxAmount))
                .build();
    }

    /**
     * Convert tax record to DTO for frontend
     */
    public TaxRecordDTO toDTO(TaxRecord record) {
        Artist artist = artistRepository.findById(record.getArtistId()).orElse(null);
        return TaxRecordDTO.builder()
                .id(record.getId())
                .artistId(record.getArtistId())
                .artistName(artist != null ? artist.getName() : "Unknown")
                .grossRevenue(record.getGrossRevenue())
                .youtubeRevenue(record.getYoutubeRevenue())
                .adsenseRevenue(record.getAdsenseRevenue())
                .taxableIncome(record.getTaxableIncome())
                .taxRate(record.getTaxRate())
                .taxAmount(record.getTaxAmount())
                .netRevenue(record.getNetRevenue())
                .period(record.getPeriod())
                .taxYear(record.getTaxYear())
                .taxQuarter(record.getTaxQuarter())
                .calculatedAt(record.getCalculatedAt())
                .paymentStatus(record.getPaymentStatus().name())
                .dueDate(record.getDueDate())
                .paidDate(record.getPaidDate())
                .videoCount(record.getVideoCount())
                .shotCount(record.getShotCount())
                .filed(record.isFiled())
                .filedDate(record.getFiledDate())
                .build();
    }

    public List<TaxRecordDTO> toDTOs(List<TaxRecord> records) {
        return records.stream().map(this::toDTO).toList();
    }

    /**
     * Convert tax record to calculation result DTO
     */
    private TaxCalculationResult toCalculationResult(TaxRecord record) {
        return TaxCalculationResult.builder()
                .taxRecordId(record.getId())
                .artistId(record.getArtistId())
                .period(record.getPeriod())
                .grossRevenue(record.getGrossRevenue())
                .taxAmount(record.getTaxAmount())
                .netRevenue(record.getNetRevenue())
                .taxRate(record.getTaxRate())
                .taxType(record.getTaxType() != null ? record.getTaxType() : "STANDARD")
                .paymentStatus(record.getPaymentStatus().name())
                .dueDate(record.getDueDate())
                .success(true)
                .message("Tax calculated successfully")
                .build();
    }

    /**
     * Update compliance status after tax calculation/payment
     */
    public void updateComplianceStatus(String artistId) {
        ComplianceStatus status = complianceRepository.findByArtistId(artistId)
                .orElseGet(() -> {
                    ComplianceStatus newStatus = ComplianceStatus.builder()
                            .artistId(artistId)
                            .registeredForDigitalTax(true)
                            .registeredDate(LocalDateTime.now())
                            .build();
                    return complianceRepository.save(newStatus);
                });

        // Calculate totals
        List<TaxRecord> allRecords = taxRecordRepository.findByArtistId(artistId);
        List<TaxRecord> outstanding = taxRecordRepository.findOutstandingTaxRecords(artistId);

        double totalRevenue = allRecords.stream()
                .mapToDouble(r -> r.getGrossRevenue() != null ? r.getGrossRevenue() : 0.0)
                .sum();

        double totalTaxPaid = allRecords.stream()
                .filter(r -> r.getPaymentStatus() == TaxRecord.PaymentStatus.PAID)
                .mapToDouble(r -> r.getTaxAmount() != null ? r.getTaxAmount() : 0.0)
                .sum();

        double outstandingTax = outstanding.stream()
                .mapToDouble(r -> r.getTaxAmount() != null ? r.getTaxAmount() : 0.0)
                .sum();

        // Calculate video/shot metrics
        List<Video> allVideos = videoMetadataRepository.findByArtistId(artistId);
        int totalVideos = allVideos.size();
        int totalShots = (int) allVideos.stream()
                .filter(v -> isShort(v.getDuration()))
                .count();

        // Check compliance
        boolean hasOverdue = outstanding.stream()
                .anyMatch(r -> r.getPaymentStatus() == TaxRecord.PaymentStatus.OVERDUE);

        int missedPayments = (int) outstanding.stream()
                .filter(r -> r.getPaymentStatus() == TaxRecord.PaymentStatus.OVERDUE)
                .count();

        boolean taxCompliant = !hasOverdue && outstandingTax == 0.0;

        // Determine compliance level
        ComplianceStatus.ComplianceLevel level;
        if (missedPayments == 0) {
            level = ComplianceStatus.ComplianceLevel.EXCELLENT;
        } else if (missedPayments <= 2) {
            level = ComplianceStatus.ComplianceLevel.GOOD;
        } else if (missedPayments <= 5) {
            level = ComplianceStatus.ComplianceLevel.FAIR;
        } else {
            level = ComplianceStatus.ComplianceLevel.POOR;
        }

        // Update status
        status.setTaxCompliant(taxCompliant);
        status.setComplianceLevel(level);
        status.setTotalRevenueToDate(totalRevenue);
        status.setTotalTaxPaid(totalTaxPaid);
        status.setOutstandingTax(outstandingTax);
        status.setHasOverduePayments(hasOverdue);
        status.setNeedsAttention(hasOverdue || outstandingTax > 0);
        status.setMissedPayments(missedPayments);
        status.setTotalVideosToDate(totalVideos);
        status.setTotalShotsToDate(totalShots);
        status.setLastTaxCalculation(LocalDateTime.now());
        status.setUpdatedAt(LocalDateTime.now());

        complianceRepository.save(status);
        log.info("Updated compliance status for artist {}: compliant={}, outstanding=${}",
                artistId, taxCompliant, outstandingTax);
    }

    /**
     * Determine if a video is a "Short" based on ISO 8601 duration
     * Shorts are typically < 60 seconds (PT1M)
     */
    private boolean isShort(String duration) {
        if (duration == null || duration.isEmpty())
            return false;
        try {
            // Simple parsing for common YouTube durations: PT#S, PT#M#S, PT#M
            if (duration.contains("H"))
                return false;

            if (duration.contains("M")) {
                int mIndex = duration.indexOf("M");
                int pIndex = duration.indexOf("T");
                String minutesStr = duration.substring(pIndex + 1, mIndex);
                int minutes = Integer.parseInt(minutesStr);

                if (minutes >= 1) {
                    if (minutes > 1)
                        return false;
                    if (duration.contains("S")) {
                        String secondsStr = duration.substring(mIndex + 1, duration.indexOf("S"));
                        return Integer.parseInt(secondsStr) == 0;
                    }
                    return true;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}