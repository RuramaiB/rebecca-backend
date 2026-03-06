package com.example.taxbackend.models;

import com.example.taxbackend.enums.AuthStatus;
import com.example.taxbackend.user.Role;
import lombok.Data;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Artist Entity - Represents a musician who has authorized the system
 */
@Data
@Builder
@Document(collection = "artists")
public class Artist {
    @Id
    private String id;

    @Indexed(unique = true)
    private String googleAccountId; // Google user ID

    private String email;
    private String name;

    @Indexed(unique = true)
    private String youtubeChannelId;

    private String youtubeChannelTitle;
    private String adsenseAccountId; // May be null if not linked

    private LocalDateTime authorizedAt;
    private LocalDateTime lastSyncedAt;

    // OAuth tokens stored securely
    private String encryptedAccessToken;
    private String encryptedRefreshToken;
    private LocalDateTime tokenExpiresAt;

    // Authorization status
    private boolean youtubeAuthorized;
    private boolean analyticsAuthorized;
    private boolean adsenseAuthorized;
    private AuthStatus authStatus;
    private Role role;
}
