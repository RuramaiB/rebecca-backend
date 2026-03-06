package com.example.taxbackend.service;


import com.example.taxbackend.models.*;
import com.example.taxbackend.repository.*;
import com.example.taxbackend.dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Risk Analysis Service - AI Compliance Risk Detection
 *
 * Academic Note: This service integrates Python ML engine with Spring Boot
 * Uses ProcessBuilder to execute Python script and parse JSON results
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskAnalysisService {

    private final ArtistRepository artistRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final RevenueRepository revenueRepository;
    private final ComplianceStatusRepository complianceRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final ObjectMapper objectMapper;

    @Value("${python.executable:C:/Python314/python.exe}")
    private String pythonExecutable;

    @Value("${python.risk.script:C:/Users/rbotso.ZBFH/Desktop/nomhani/tax-backend/src/main/java/com/example/taxbackend/scripts/risk_engine.py}")
    private String pythonScriptPath;

    @Value("${risk.assessment.cache.hours:24}")
    private int cacheHours;

    /**
     * Analyze compliance risk for specific artist
     *
     * @param artistId Artist to analyze
     * @param forceReassessment Bypass cache
     * @return Risk analysis result
     */
    @Transactional
    public RiskAnalysisResponse analyzeArtistRisk(String artistId, boolean forceReassessment) {
        log.info("Starting risk analysis for artist: {}", artistId);

        // Check for recent assessment (if not forcing)
        if (!forceReassessment) {
            Optional<RiskAssessment> recent = riskAssessmentRepository
                    .findFirstByArtistIdOrderByAssessedAtDesc(artistId);

            if (recent.isPresent()) {
                RiskAssessment cached = recent.get();
                long hoursSinceAssessment = ChronoUnit.HOURS.between(
                        cached.getAssessedAt(), LocalDateTime.now());

                if (hoursSinceAssessment < cacheHours) {
                    log.info("Using cached assessment from {} hours ago", hoursSinceAssessment);
                    return toResponseDTO(cached);
                }
            }
        }

        try {
            // Prepare input data for Python script
            Map<String, Object> pythonInput = preparePythonInput(artistId);

            // Execute Python ML script
            String pythonOutput = executePythonScript(pythonInput);

            // Parse results
            JsonNode resultNode = objectMapper.readTree(pythonOutput);

            // Check for errors
            if (resultNode.has("error")) {
                throw new RuntimeException("Python script error: " + resultNode.get("error").asText());
            }

            // Create and save risk assessment
            RiskAssessment assessment = createAssessmentFromPython(resultNode, artistId);
            assessment = riskAssessmentRepository.save(assessment);

            log.info("Risk analysis completed: {} - {} (score: {})",
                    artistId, assessment.getRiskLevel(), assessment.getRiskScore());

            return toResponseDTO(assessment);

        } catch (Exception e) {
            log.error("Error analyzing artist risk: {}", e.getMessage(), e);
            throw new RuntimeException("Risk analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze all active artists
     * Used by scheduler for bulk assessment
     */
    @Transactional
    public List<RiskAnalysisResponse> analyzeAllArtists() {
        log.info("Starting bulk risk analysis for all artists");

        List<Artist> allArtists = artistRepository.findByYoutubeAuthorizedTrue();
        List<RiskAnalysisResponse> results = new ArrayList<>();

        int successCount = 0;
        int failureCount = 0;

        for (Artist artist : allArtists) {
            try {
                RiskAnalysisResponse result = analyzeArtistRisk(artist.getId(), true);
                results.add(result);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to analyze artist {}: {}", artist.getId(), e.getMessage());
                failureCount++;
            }
        }

        log.info("Bulk analysis complete: {} succeeded, {} failed", successCount, failureCount);
        return results;
    }

    /**
     * Get risk dashboard summary
     */
    public RiskDashboardDTO getRiskDashboard() {
        List<RiskAssessment> allAssessments = riskAssessmentRepository.findAll();

        // Get latest assessment per artist
        Map<String, RiskAssessment> latestPerArtist = new HashMap<>();
        for (RiskAssessment assessment : allAssessments) {
            String artistId = assessment.getArtistId();
            if (!latestPerArtist.containsKey(artistId) ||
                    assessment.getAssessedAt().isAfter(latestPerArtist.get(artistId).getAssessedAt())) {
                latestPerArtist.put(artistId, assessment);
            }
        }

        List<RiskAssessment> latestAssessments = new ArrayList<>(latestPerArtist.values());

        // Calculate statistics
        long lowCount = latestAssessments.stream()
                .filter(a -> "LOW".equals(a.getRiskLevel())).count();
        long mediumCount = latestAssessments.stream()
                .filter(a -> "MEDIUM".equals(a.getRiskLevel())).count();
        long highCount = latestAssessments.stream()
                .filter(a -> "HIGH".equals(a.getRiskLevel())).count();

        long flaggedCount = latestAssessments.stream()
                .filter(RiskAssessment::isFlaggedForAudit).count();

        long pendingReview = latestAssessments.stream()
                .filter(a -> a.isFlaggedForAudit() && !a.isReviewed()).count();

        double avgScore = latestAssessments.stream()
                .mapToDouble(RiskAssessment::getRiskScore)
                .average()
                .orElse(0.0);

        // Get high-risk artists
        List<RiskAnalysisResponse> highRisk = latestAssessments.stream()
                .filter(a -> "HIGH".equals(a.getRiskLevel()))
                .sorted(Comparator.comparingDouble(RiskAssessment::getRiskScore).reversed())
                .limit(10)
                .map(this::toResponseDTO)
                .collect(Collectors.toList());

        LocalDateTime lastAssessment = latestAssessments.stream()
                .map(RiskAssessment::getAssessedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return RiskDashboardDTO.builder()
                .totalArtists((int) artistRepository.count())
                .assessedArtists(latestAssessments.size())
                .lowRiskCount((int) lowCount)
                .mediumRiskCount((int) mediumCount)
                .highRiskCount((int) highCount)
                .flaggedForAudit((int) flaggedCount)
                .pendingReview((int) pendingReview)
                .averageRiskScore(Math.round(avgScore * 1000.0) / 1000.0)
                .highRiskArtists(highRisk)
                .lastAssessment(lastAssessment)
                .nextScheduledAssessment(LocalDateTime.now().plusDays(30))
                .build();
    }

    /**
     * Prepare input data for Python ML script
     */
    private Map<String, Object> preparePythonInput(String targetArtistId) {
        // Get target artist data
        Artist targetArtist = artistRepository.findById(targetArtistId)
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + targetArtistId));

        ArtistRiskInputDTO targetData = buildArtistRiskData(targetArtist);

        // Get all artists for comparison
        List<Artist> allArtists = artistRepository.findAll();
        List<ArtistRiskInputDTO> allArtistData = allArtists.stream()
                .map(this::buildArtistRiskData)
                .collect(Collectors.toList());

        Map<String, Object> input = new HashMap<>();
        input.put("targetArtist", targetData);
        input.put("allArtists", allArtistData);

        return input;
    }

    /**
     * Build risk input data for an artist
     */
    private ArtistRiskInputDTO buildArtistRiskData(Artist artist) {
        String artistId = artist.getId();

        // Get compliance status
        ComplianceStatus compliance = complianceRepository.findByArtistId(artistId)
                .orElse(new ComplianceStatus());

        // Get recent revenue records (last 12 months)
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<RevenueRecord> revenues = revenueRepository
                .findByArtistIdAndPeriodStartBetween(artistId, oneYearAgo, LocalDateTime.now());

        // Group revenues by month
        List<Double> monthlyRevenues = revenues.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getPeriodStart().getMonth(),
                        Collectors.summingDouble(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : 0.0)
                ))
                .values().stream()
                .collect(Collectors.toList());

        // Get tax records for recalculation count
        List<TaxRecord> taxRecords = taxRecordRepository.findByArtistId(artistId);
        int recalcCount = taxRecords.size();

        // Calculate days since registration
        long daysSinceReg = ChronoUnit.DAYS.between(
                artist.getAuthorizedAt(), LocalDateTime.now());

        return ArtistRiskInputDTO.builder()
                .artistId(artistId)
                .googleAccountId(artist.getGoogleAccountId())
                .paypalEmail(null) // Add if you have this field
                .monthlyRevenues(monthlyRevenues)
                .taxThreshold(100.0) // From tax configuration
                .missedTaxPeriods(Math.max(compliance.getMissedPayments(), 0))
                .taxPaymentRatio(calculateTaxPaymentRatio(artistId))
                .consecutiveCompliantMonths(Math.max(compliance.getConsecutiveCompliantMonths(), 0))
                .totalRevenueToDate(compliance.getTotalRevenueToDate() != null ?
                        compliance.getTotalRevenueToDate() : 0.0)
                .totalTaxPaid(compliance.getTotalTaxPaid() != null ?
                        compliance.getTotalTaxPaid() : 0.0)
                .outstandingTax(compliance.getOutstandingTax() != null ?
                        compliance.getOutstandingTax() : 0.0)
                .recalculationCount(recalcCount)
                .daysSinceRegistration((int) daysSinceReg)
                .build();
    }

    /**
     * Calculate tax payment ratio
     */
    private Double calculateTaxPaymentRatio(String artistId) {
        List<TaxRecord> allTax = taxRecordRepository.findByArtistId(artistId);
        if (allTax.isEmpty()) return 1.0;

        long paidCount = allTax.stream()
                .filter(t -> TaxRecord.PaymentStatus.PAID.equals(t.getPaymentStatus()))
                .count();

        return (double) paidCount / allTax.size();
    }

    /**
     * Execute Python ML script using ProcessBuilder
     */
    private String executePythonScript(Map<String, Object> input)
            throws IOException, InterruptedException {

        String inputJson = objectMapper.writeValueAsString(input);

        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable,
                "-u",
                pythonScriptPath
        );

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        try (Writer writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write(inputJson);
            writer.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("Python failed with exit code {}", exitCode);
            log.error("Python output:\n{}", output);
            throw new RuntimeException(
                    "Python script failed with exit code " + exitCode + ":\n" + output
            );
        }

        return output.toString();
    }


    /**
     * Create RiskAssessment entity from Python output
     */
    private RiskAssessment createAssessmentFromPython(JsonNode pythonOutput, String artistId) {
        // Get previous assessment for trend calculation
        Optional<RiskAssessment> previous = riskAssessmentRepository
                .findFirstByArtistIdOrderByAssessedAtDesc(artistId);

        double riskScore = pythonOutput.get("riskScore").asDouble();
        String riskLevel = pythonOutput.get("riskLevel").asText();

        List<String> indicators = new ArrayList<>();
        if (pythonOutput.has("indicators")) {
            pythonOutput.get("indicators").forEach(ind -> indicators.add(ind.asText()));
        }

        RiskAssessment assessment = RiskAssessment.builder()
                .artistId(artistId)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .mlAnomalyScore(pythonOutput.has("mlAnomalyScore") ?
                        pythonOutput.get("mlAnomalyScore").asDouble() : null)
                .ruleBasedScore(pythonOutput.has("ruleBasedScore") ?
                        pythonOutput.get("ruleBasedScore").asDouble() : null)
                .indicators(indicators)
                .assessedAt(LocalDateTime.now())
                .analysisVersion(pythonOutput.has("analysisVersion") ?
                        pythonOutput.get("analysisVersion").asText() : "1.0")
                .artistsAnalyzed(pythonOutput.has("artistsAnalyzed") ?
                        pythonOutput.get("artistsAnalyzed").asInt() : null)
                .flaggedForAudit("HIGH".equals(riskLevel))
                .reviewed(false)
                .build();

        // Calculate trend
        if (previous.isPresent()) {
            assessment.setPreviousRiskLevel(previous.get().getRiskLevel());
            assessment.setRiskTrend(riskScore - previous.get().getRiskScore());
        }

        return assessment;
    }

    /**
     * Convert RiskAssessment to Response DTO
     */
    private RiskAnalysisResponse toResponseDTO(RiskAssessment assessment) {
        Artist artist = artistRepository.findById(assessment.getArtistId()).orElse(null);

        String recommendation = generateRecommendation(assessment);

        return RiskAnalysisResponse.builder()
                .artistId(assessment.getArtistId())
                .artistName(artist != null ? artist.getName() : "Unknown")
                .riskScore(assessment.getRiskScore())
                .riskLevel(assessment.getRiskLevel())
                .mlAnomalyScore(assessment.getMlAnomalyScore())
                .ruleBasedScore(assessment.getRuleBasedScore())
                .indicators(assessment.getIndicators())
                .assessedAt(assessment.getAssessedAt())
                .analysisVersion(assessment.getAnalysisVersion())
                .artistsAnalyzed(assessment.getArtistsAnalyzed())
                .flaggedForAudit(assessment.isFlaggedForAudit())
                .recommendation(recommendation)
                .build();
    }

    /**
     * Generate human-readable recommendation
     */
    private String generateRecommendation(RiskAssessment assessment) {
        String level = assessment.getRiskLevel();

        switch (level) {
            case "HIGH":
                return "IMMEDIATE ACTION REQUIRED: Flag for manual audit and review all transactions";
            case "MEDIUM":
                return "MONITOR CLOSELY: Schedule review within 30 days and track behavior changes";
            case "LOW":
                return "NO ACTION NEEDED: Continue routine monitoring";
            default:
                return "Review assessment details";
        }
    }
}
