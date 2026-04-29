package com.example.taxbackend.controller;

import com.example.taxbackend.dtos.*;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.models.TaxRecord;
import com.example.taxbackend.service.ArtistService;
import com.example.taxbackend.service.TaxScheduler;
import com.example.taxbackend.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tax")
@Slf4j
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;
    private final ArtistService artistService;
    private final TaxScheduler taxScheduler;

    /**
     * Calculate tax for artist and period
     * POST /api/tax/calculate
     */
    @PostMapping("/calculate")
    public ResponseEntity<?> calculateTax(
            @RequestParam String artistId,
            @RequestParam String period) {

        try {
            TaxCalculationResult result = taxService.calculateTax(artistId, period);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error calculating tax", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Batch calculate tax for all artists
     * POST /api/tax/calculate/batch
     */
    @PostMapping("/calculate/batch")
    public ResponseEntity<?> calculateBatchTax(@RequestBody BatchTaxCalculationDTO request) {
        try {
            List<TaxCalculationResult> results = taxService.calculateTaxForAllArtists(request.getPeriod());

            long successful = results.stream().filter(TaxCalculationResult::isSuccess).count();
            long failed = results.size() - successful;

            BatchTaxCalculationResponseDTO response = BatchTaxCalculationResponseDTO.builder()
                    .period(request.getPeriod())
                    .totalArtists(results.size())
                    .successfulCalculations((int) successful)
                    .failedCalculations((int) failed)
                    .results(results)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in batch tax calculation", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/get-all-tax-records")
    public ResponseEntity<?> getAllTaxRecords() {
        try {
            List<TaxRecord> records = taxService.getTaxRecords();
            List<TaxRecordDTO> dtos = taxService.toDTOs(records);

            return ResponseEntity.ok(Map.of(
                    "count", dtos.size(),
                    "records", dtos));

        } catch (Exception e) {
            log.error("Error fetching all tax records", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch tax records"));
        }
    }

    /**
     * Get tax records for artist
     * GET /api/tax/{artistId}
     */
    @GetMapping("/{artistId}")
    public ResponseEntity<?> getTaxRecords(@PathVariable String artistId) {
        try {
            List<TaxRecord> records = taxService.getTaxRecords(artistId);
            List<TaxRecordDTO> dtos = taxService.toDTOs(records);

            return ResponseEntity.ok(Map.of(
                    "artistId", artistId,
                    "count", dtos.size(),
                    "records", dtos));

        } catch (Exception e) {
            log.error("Error fetching tax records", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch tax records"));
        }
    }

    /**
     * Get tax record for specific period
     * GET /api/tax/{artistId}/{period}
     */
    @GetMapping("/{artistId}/{period}")
    public ResponseEntity<?> getTaxRecord(
            @PathVariable String artistId,
            @PathVariable String period) {

        try {
            return taxService.getTaxRecord(artistId, period)
                    .map(taxService::toDTO)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("Error fetching tax record", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch tax record"));
        }
    }

    /**
     * Get outstanding tax for artist
     * GET /api/tax/{artistId}/outstanding
     */
    @GetMapping("/{artistId}/outstanding")
    public ResponseEntity<?> getOutstandingTax(@PathVariable String artistId) {
        try {
            List<TaxRecord> outstanding = taxService.getOutstandingTax(artistId);

            double totalOutstanding = outstanding.stream()
                    .mapToDouble(r -> r.getTaxAmount() != null ? r.getTaxAmount() : 0.0)
                    .sum();

            return ResponseEntity.ok(Map.of(
                    "artistId", artistId,
                    "outstandingPayments", outstanding.size(),
                    "totalOutstanding", totalOutstanding,
                    "records", outstanding));

        } catch (Exception e) {
            log.error("Error fetching outstanding tax", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch outstanding tax"));
        }
    }

    /**
     * Mark tax as paid
     * POST /api/tax/payment
     */
    @PostMapping("/payment")
    public ResponseEntity<?> markAsPaid(@RequestBody TaxPaymentDTO payment) {
        try {
            taxService.markAsPaid(
                    payment.getTaxRecordId(),
                    payment.getPaymentReference(),
                    payment.getPaymentMethod());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment recorded successfully"));

        } catch (Exception e) {
            log.error("Error recording payment", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to record payment"));
        }
    }

    /**
     * Trigger manual tax calculation (admin function)
     * POST /api/tax/trigger?period=2024-12
     */
    @PostMapping("/trigger")
    public ResponseEntity<?> triggerCalculation(@RequestParam String period) {
        try {
            taxScheduler.triggerManualCalculation(period);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tax calculation triggered for period: " + period));

        } catch (Exception e) {
            log.error("Error triggering calculation", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to trigger calculation"));
        }
    }

    /**
     * Recalculate all tax records for all artists (system reset)
     * POST /api/tax/recalculate-all
     */
    @PostMapping("/recalculate-all")
    public ResponseEntity<?> recalculateAll() {
        try {
            log.info("Triggering global tax recalculation (reset)");
            taxService.recalculateAllArtistsFromOnboarding();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Global tax recalculation triggered successfully"));
        } catch (Exception e) {
            log.error("Error triggering global recalculation", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to trigger global recalculation"));
        }
    }
}
