package com.example.taxbackend.dtos;
import com.example.taxbackend.models.TaxRecord;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaxCalculationResult {
    private String taxRecordId;
    private String artistId;
    private String period;

    private Double grossRevenue;
    private Double taxAmount;
    private Double netRevenue;
    private Double taxRate;
    private String taxType;

    private String paymentStatus;
    private LocalDateTime dueDate;

    private boolean success;
    private String message;
}
