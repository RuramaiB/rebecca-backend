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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaxRecordDTO {
    private String id;
    private String artistId;
    private String artistName;

    private Double grossRevenue;
    private Double youtubeRevenue;
    private Double adsenseRevenue;

    private Double taxableIncome;
    private Double taxRate;
    private Double taxAmount;
    private Double baseTaxAmount;
    private Double interestAmount;
    private Double netRevenue;

    private String period;
    private String taxYear;
    private String taxQuarter;

    private LocalDateTime calculatedAt;
    private String paymentStatus;
    private LocalDateTime dueDate;
    private LocalDateTime paidDate;

    private int videoCount;
    private int shotCount;

    private boolean filed;
    private LocalDateTime filedDate;
}
