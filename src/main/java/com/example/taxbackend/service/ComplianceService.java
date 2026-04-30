package com.example.taxbackend.service;

import com.example.taxbackend.dtos.*;
import com.example.taxbackend.models.*;
import com.example.taxbackend.repository.ArtistRepository;
import com.example.taxbackend.repository.ComplianceStatusRepository;
import com.example.taxbackend.repository.TaxRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceService {

        private final ComplianceStatusRepository complianceRepository;
        private final ArtistRepository artistRepository;
        private final TaxRecordRepository taxRecordRepository;
        private final TaxService taxService;

        /*
         * =====================================================
         * INITIALIZATION & RECALCULATION
         * =====================================================
         */

        /**
         * Initialize and calculate compliance for ALL artists
         */
        public List<ComplianceReportDTO> initializeAndCalculateAllCompliance() {
                List<Artist> artists = artistRepository.findAllByRoleNot(com.example.taxbackend.user.Role.ADMIN);

                List<ComplianceReportDTO> reports = artists.stream()
                                .map(artist -> {
                                        String artistId = artist.getId();

                                        // Ensure compliance record exists
                                        initializeIfMissing(artistId);
                                        
                                        // Ensure taxes are calculated for recent periods
                                        taxService.recalculateArtistFromOnboarding(artistId);
                                        
                                        // Recalculate compliance summary
                                        calculateAndUpdateCompliance(artistId);

                                        // Return up-to-date compliance report
                                        return getComplianceReport(artistId);
                                })
                                .collect(Collectors.toList());

                log.info("Compliance initialized & calculated for {} artists", reports.size());
                return reports;
        }

        private void initializeIfMissing(String artistId) {
                if (complianceRepository.findByArtistId(artistId).isPresent()) {
                        return;
                }

                ComplianceStatus status = ComplianceStatus.builder()
                                .artistId(artistId)
                                .taxCompliant(true)
                                .complianceLevel(ComplianceStatus.ComplianceLevel.EXCELLENT)
                                .complianceNotes("Initialized")
                                .totalRevenueToDate(0.0)
                                .totalTaxPaid(0.0)
                                .outstandingTax(0.0)
                                .currentYearRevenue(0.0)
                                .currentYearTax(0.0)
                                .consecutiveCompliantMonths(0)
                                .missedPayments(0)
                                .hasOverduePayments(false)
                                .needsAttention(false)
                                .registeredDate(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                complianceRepository.save(status);
        }

        /**
         * Core compliance calculation logic (based on real TaxRecord fields)
         */
        private void calculateAndUpdateCompliance(String artistId) {
                taxService.updateComplianceStatus(artistId);
        }

        private ComplianceStatus.ComplianceLevel determineComplianceLevel(int missedPayments, boolean overdue) {
                if (missedPayments == 0 && !overdue)
                        return ComplianceStatus.ComplianceLevel.EXCELLENT;
                if (missedPayments <= 1)
                        return ComplianceStatus.ComplianceLevel.GOOD;
                if (missedPayments <= 3)
                        return ComplianceStatus.ComplianceLevel.FAIR;
                if (missedPayments <= 5)
                        return ComplianceStatus.ComplianceLevel.POOR;
                return ComplianceStatus.ComplianceLevel.CRITICAL;
        }

        /*
         * =====================================================
         * REPORTING
         * =====================================================
         */

        public ComplianceReportDTO getComplianceReport(String artistId) {
                Artist artist = artistRepository.findById(artistId)
                                .orElseThrow(() -> new IllegalArgumentException("Artist not found"));

                ComplianceStatus status = complianceRepository.findByArtistId(artistId)
                                .orElseThrow(() -> new IllegalStateException("Compliance not initialized"));

                List<AlertDTO> alerts = status.getAlerts() == null
                                ? List.of()
                                : status.getAlerts().stream()
                                                .map(this::toAlertDTO)
                                                .collect(Collectors.toList());

                return ComplianceReportDTO.builder()
                                .complianceStatusId(status.getId())
                                .artistId(artistId)
                                .artistName(artist.getName())
                                .email(artist.getEmail())
                                .taxCompliant(status.isTaxCompliant())
                                .complianceLevel(status.getComplianceLevel().name())
                                .complianceNotes(status.getComplianceNotes())
                                .totalRevenueToDate(status.getTotalRevenueToDate())
                                .totalTaxPaid(status.getTotalTaxPaid())
                                .outstandingTax(status.getOutstandingTax())
                                .totalTaxDue((status.getTotalTaxPaid() != null ? status.getTotalTaxPaid() : 0.0) + (status.getOutstandingTax() != null ? status.getOutstandingTax() : 0.0))
                                .totalVideosToDate(status.getTotalVideosToDate())
                                .totalShotsToDate(status.getTotalShotsToDate())
                                .lastTaxCalculation(status.getLastTaxCalculation())
                                .lastTaxPayment(status.getLastTaxPayment())
                                .nextTaxDue(status.getNextTaxDue())
                                .lastFiledPeriod(status.getLastFiledPeriod())
                                .consecutiveCompliantMonths(status.getConsecutiveCompliantMonths())
                                .missedPayments(status.getMissedPayments())
                                .alerts(alerts)
                                .hasOverduePayments(status.isHasOverduePayments())
                                .needsAttention(status.isNeedsAttention())
                                .taxIdentificationNumber(status.getTaxIdentificationNumber())
                                .registeredDate(status.getRegisteredDate())
                                .registeredForDigitalTax(status.isRegisteredForDigitalTax())
                                .build();
        }

        public List<ComplianceReportDTO> getNonCompliantArtists() {
                return complianceRepository.findByTaxCompliantFalse()
                                .stream()
                                .map(s -> getComplianceReport(s.getArtistId()))
                                .collect(Collectors.toList());
        }

        public List<ComplianceReportDTO> getOverdueArtists() {
                return complianceRepository.findByHasOverduePaymentsTrue()
                                .stream()
                                .map(s -> getComplianceReport(s.getArtistId()))
                                .collect(Collectors.toList());
        }

        public List<ComplianceReportDTO> getArtistsNeedingAttention() {
                return complianceRepository.findByNeedsAttentionTrue()
                                .stream()
                                .map(s -> getComplianceReport(s.getArtistId()))
                                .collect(Collectors.toList());
        }

        public ComplianceStatistics getComplianceStatistics() {
                long totalArtists = artistRepository.countByYoutubeAuthorizedTrueAndRoleNot(true,
                                com.example.taxbackend.user.Role.ADMIN); // Approximate for all artists
                // More accurate:
                totalArtists = artistRepository.findAllByRoleNot(com.example.taxbackend.user.Role.ADMIN).size();

                // We need methods in ComplianceStatusRepository or filter here
                List<ComplianceStatus> allStatus = complianceRepository.findAll();
                List<String> adminIds = artistRepository.findAllByRole(com.example.taxbackend.user.Role.ADMIN)
                                .stream().map(Artist::getId).toList();

                List<ComplianceStatus> artistStatus = allStatus.stream()
                                .filter(s -> !adminIds.contains(s.getArtistId()))
                                .toList();

                long compliant = artistStatus.stream().filter(ComplianceStatus::isTaxCompliant).count();
                long nonCompliant = artistStatus.size() - compliant;

                double totalRevenue = artistStatus.stream()
                                .mapToDouble(s -> s.getTotalRevenueToDate() != null ? s.getTotalRevenueToDate() : 0.0)
                                .sum();

                double totalTaxPaid = artistStatus.stream()
                                .mapToDouble(s -> s.getTotalTaxPaid() != null ? s.getTotalTaxPaid() : 0.0)
                                .sum();

                double totalOutstanding = artistStatus.stream()
                                .mapToDouble(s -> s.getOutstandingTax() != null ? s.getOutstandingTax() : 0.0)
                                .sum();

                return ComplianceStatistics.builder()
                                .totalArtists((int) totalArtists)
                                .compliantArtists((int) compliant)
                                .nonCompliantArtists((int) nonCompliant)
                                .complianceRate(totalArtists > 0
                                                ? (double) compliant / totalArtists * 100
                                                : 0.0)
                                .totalRevenueTracked(totalRevenue)
                                .totalTaxCollected(totalTaxPaid)
                                .totalOutstandingTax(totalOutstanding)
                                .build();
        }

        private AlertDTO toAlertDTO(ComplianceAlert alert) {
                return AlertDTO.builder()
                                .type(alert.getType().name())
                                .message(alert.getMessage())
                                .severity(alert.getSeverity().name())
                                .createdAt(alert.getCreatedAt())
                                .acknowledged(alert.isAcknowledged())
                                .build();
        }
}
