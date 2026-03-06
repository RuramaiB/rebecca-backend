package com.example.taxbackend.auditing;

import lombok.Data;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document
public abstract class AuditorEntity {

    @CreatedDate
    private LocalDateTime createdAt;

    @CreatedBy
    private String createdByEmail;

    @LastModifiedDate
    private LocalDateTime updateAt;

    @LastModifiedBy
    private String updatedBy;

    @Version
    private  Integer version;
}
