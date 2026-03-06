package com.example.taxbackend.controller;

import com.example.taxbackend.dtos.ComplianceReportDTO;
import com.example.taxbackend.dtos.ComplianceStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.taxbackend.service.ComplianceService;

import java.util.List;
import java.util.Map;

/**
 * Compliance Controller
 * Provides endpoints for tax compliance monitoring
 */
@RestController
@RequestMapping("/api/compliance")
@Slf4j
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    /**
     * Get compliance report for specific artist
     * GET /api/compliance/{artistId}
     */
    @GetMapping("/{artistId}")
    public ResponseEntity<?> getArtistCompliance(@PathVariable String artistId) {
        try {
            ComplianceReportDTO report = complianceService.getComplianceReport(artistId);
            return ResponseEntity.ok(report);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching compliance report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch compliance report"));
        }
    }

    @PostMapping("/initialize-all-compliance")
    public List<ComplianceReportDTO> recalculateAllCompliance() {
        return complianceService.initializeAndCalculateAllCompliance();
    }

    /**
     * Get all non-compliant artists
     * GET /api/compliance/non-compliant
     */
    @GetMapping("/non-compliant")
    public ResponseEntity<?> getNonCompliantArtists() {
        try {
            List<ComplianceReportDTO> reports = complianceService.getNonCompliantArtists();

            return ResponseEntity.ok(Map.of(
                    "count", reports.size(),
                    "artists", reports
            ));

        } catch (Exception e) {
            log.error("Error fetching non-compliant artists", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch non-compliant artists"));
        }
    }

    /**
     * Get artists with overdue payments
     * GET /api/compliance/overdue
     */
    @GetMapping("/overdue")
    public ResponseEntity<?> getOverdueArtists() {
        try {
            List<ComplianceReportDTO> reports = complianceService.getOverdueArtists();

            return ResponseEntity.ok(Map.of(
                    "count", reports.size(),
                    "artists", reports
            ));

        } catch (Exception e) {
            log.error("Error fetching overdue artists", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch overdue artists"));
        }
    }

    /**
     * Get artists needing attention
     * GET /api/compliance/attention
     */
    @GetMapping("/attention")
    public ResponseEntity<?> getArtistsNeedingAttention() {
        try {
            List<ComplianceReportDTO> reports = complianceService.getArtistsNeedingAttention();

            return ResponseEntity.ok(Map.of(
                    "count", reports.size(),
                    "artists", reports
            ));

        } catch (Exception e) {
            log.error("Error fetching artists needing attention", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch artists needing attention"));
        }
    }

    /**
     * Get overall compliance statistics
     * GET /api/compliance/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getComplianceStatistics() {
        try {
            ComplianceStatistics stats =
                    complianceService.getComplianceStatistics();

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error fetching compliance statistics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch statistics"));
        }
    }

    /**
     * Get comprehensive compliance overview
     * GET /api/compliance
     */
    @GetMapping
    public ResponseEntity<?> getComplianceOverview() {
        try {
            ComplianceStatistics stats =
                    complianceService.getComplianceStatistics();

            List<ComplianceReportDTO> nonCompliant =
                    complianceService.getNonCompliantArtists();

            List<ComplianceReportDTO> overdue =
                    complianceService.getOverdueArtists();

            return ResponseEntity.ok(Map.of(
                    "statistics", stats,
                    "nonCompliantCount", nonCompliant.size(),
                    "overdueCount", overdue.size(),
                    "nonCompliantArtists", nonCompliant,
                    "overdueArtists", overdue
            ));

        } catch (Exception e) {
            log.error("Error fetching compliance overview", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch compliance overview"));
        }
    }
}
