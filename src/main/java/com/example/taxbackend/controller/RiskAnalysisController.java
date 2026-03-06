package com.example.taxbackend.controller;

import com.example.taxbackend.service.RiskAnalysisService;
import com.example.taxbackend.dtos.*;
import com.example.taxbackend.models.RiskAssessment;
import com.example.taxbackend.repository.RiskAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Risk Analysis Controller
 * REST API for AI compliance risk detection
 *
 * Academic Note: Demonstrates integration of ML-powered compliance
 * monitoring in a Spring Boot application
 */
@RestController
@RequestMapping("/api/risk")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RiskAnalysisController {

    private final RiskAnalysisService riskAnalysisService;
    private final RiskAssessmentRepository riskAssessmentRepository;

    /**
     * Analyze risk for specific artist
     *
     * POST /api/risk/analyze/{artistId}
     */
    @PostMapping("/analyze/{artistId}")
    public ResponseEntity<?> analyzeArtist(
            @PathVariable String artistId,
            @RequestParam(defaultValue = "false") boolean forceReassessment) {

        try {
            log.info("Risk analysis requested for artist: {}", artistId);

            RiskAnalysisResponse result = riskAnalysisService.analyzeArtistRisk(
                    artistId, forceReassessment);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("Invalid artist ID: {}", artistId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error analyzing artist risk", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Risk analysis failed: " + e.getMessage()));
        }
    }

    /**
     * Analyze all active artists (bulk)
     *
     * POST /api/risk/analyze/all
     */
    @PostMapping("/analyze/all")
    public ResponseEntity<?> analyzeAllArtists() {
        try {
            log.info("Bulk risk analysis initiated");

            List<RiskAnalysisResponse> results = riskAnalysisService.analyzeAllArtists();

            Map<String, Object> response = new HashMap<>();
            response.put("totalAnalyzed", results.size());
            response.put("results", results);

            // Summary statistics
            long highRisk = results.stream()
                    .filter(r -> "HIGH".equals(r.getRiskLevel())).count();
            long mediumRisk = results.stream()
                    .filter(r -> "MEDIUM".equals(r.getRiskLevel())).count();
            long lowRisk = results.stream()
                    .filter(r -> "LOW".equals(r.getRiskLevel())).count();

            response.put("summary", Map.of(
                    "highRisk", highRisk,
                    "mediumRisk", mediumRisk,
                    "lowRisk", lowRisk
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in bulk risk analysis", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get risk assessment history for artist
     *
     * GET /api/risk/history/{artistId}
     */
    @GetMapping("/history/{artistId}")
    public ResponseEntity<?> getAssessmentHistory(@PathVariable String artistId) {
        try {
            List<RiskAssessment> history = riskAssessmentRepository
                    .findByArtistIdOrderByAssessedAtDesc(artistId);

            Map<String, Object> response = new HashMap<>();
            response.put("artistId", artistId);
            response.put("assessmentCount", history.size());
            response.put("assessments", history);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching assessment history", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get risk dashboard summary
     *
     * GET /api/risk/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        try {
            RiskDashboardDTO dashboard = riskAnalysisService.getRiskDashboard();
            return ResponseEntity.ok(dashboard);

        } catch (Exception e) {
            log.error("Error fetching risk dashboard", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get high-risk artists
     *
     * GET /api/risk/high-risk
     */
    @GetMapping("/high-risk")
    public ResponseEntity<?> getHighRiskArtists() {
        try {
            List<RiskAssessment> highRisk = riskAssessmentRepository
                    .findByRiskLevelOrderByRiskScoreDesc("HIGH");

            Map<String, Object> response = new HashMap<>();
            response.put("count", highRisk.size());
            response.put("artists", highRisk);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching high-risk artists", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get artists flagged for audit
     *
     * GET /api/risk/flagged
     */
    @GetMapping("/flagged")
    public ResponseEntity<?> getFlaggedArtists() {
        try {
            List<RiskAssessment> flagged = riskAssessmentRepository
                    .findByFlaggedForAuditTrue();

            long pendingReview = flagged.stream()
                    .filter(a -> !a.isReviewed()).count();

            Map<String, Object> response = new HashMap<>();
            response.put("totalFlagged", flagged.size());
            response.put("pendingReview", pendingReview);
            response.put("artists", flagged);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching flagged artists", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark assessment as reviewed
     *
     * PUT /api/risk/review/{assessmentId}
     */
    @PutMapping("/review/{assessmentId}")
    public ResponseEntity<?> markReviewed(
            @PathVariable String assessmentId,
            @RequestParam String reviewedBy,
            @RequestParam(required = false) String notes) {

        try {
            RiskAssessment assessment = riskAssessmentRepository.findById(assessmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));

            assessment.setReviewed(true);
            assessment.setReviewedAt(java.time.LocalDateTime.now());
            assessment.setReviewedBy(reviewedBy);
            assessment.setReviewNotes(notes);

            riskAssessmentRepository.save(assessment);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Assessment marked as reviewed"
            ));

        } catch (Exception e) {
            log.error("Error marking assessment as reviewed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get risk statistics
     *
     * GET /api/risk/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        try {
            long totalAssessments = riskAssessmentRepository.count();
            long lowCount = riskAssessmentRepository.countByRiskLevel("LOW");
            long mediumCount = riskAssessmentRepository.countByRiskLevel("MEDIUM");
            long highCount = riskAssessmentRepository.countByRiskLevel("HIGH");

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalAssessments", totalAssessments);
            stats.put("riskDistribution", Map.of(
                    "low", lowCount,
                    "medium", mediumCount,
                    "high", highCount
            ));

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error fetching statistics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check for risk engine
     *
     * GET /api/risk/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "AI Compliance Risk Engine");
        response.put("version", "1.0");
        return ResponseEntity.ok(response);
    }
}

