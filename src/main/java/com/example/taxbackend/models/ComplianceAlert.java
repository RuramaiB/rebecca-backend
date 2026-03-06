package com.example.taxbackend.models;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComplianceAlert {
    private AlertType type;
    private String message;
    private AlertSeverity severity;
    private LocalDateTime createdAt;
    private boolean acknowledged;

    public enum AlertType {
        PAYMENT_OVERDUE,
        RETURN_NOT_FILED,
        MISSING_REVENUE_DATA,
        THRESHOLD_EXCEEDED,
        REGISTRATION_REQUIRED,
        AUDIT_SCHEDULED
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
