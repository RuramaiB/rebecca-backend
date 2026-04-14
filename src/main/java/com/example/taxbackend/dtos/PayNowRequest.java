package com.example.taxbackend.dtos;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@ToString
public class PayNowRequest {
    private String email;
    private String phoneNumber;
    private Double amount;
    private String method;
    private String narration;
    private String complianceID;

}