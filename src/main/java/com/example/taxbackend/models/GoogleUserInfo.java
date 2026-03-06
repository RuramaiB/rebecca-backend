package com.example.taxbackend.models;

@lombok.Data
@lombok.Builder
public class GoogleUserInfo {
    private String googleAccountId;
    private String email;
    private String name;
    private String picture;
    private boolean emailVerified;
    private String role;
}
