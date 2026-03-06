package com.example.taxbackend.dtos;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatchTaxCalculationResponseDTO {
    private String period;
    private int totalArtists;
    private int successfulCalculations;
    private int failedCalculations;
    private List<TaxCalculationResult> results;
}
