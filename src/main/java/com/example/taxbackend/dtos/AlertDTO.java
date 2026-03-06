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
public class AlertDTO {
    private String type;
    private String message;
    private String severity;
    private LocalDateTime createdAt;
    private boolean acknowledged;
}
