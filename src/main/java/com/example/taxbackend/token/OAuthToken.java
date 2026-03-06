package com.example.taxbackend.token;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Document(collection = "oauth_tokens")
public class OAuthToken {
    @Id
    private String id;

    @Indexed(unique = true)
    private String artistId;

    private String accessToken;
    private String refreshToken;
    private String tokenType; // Usually "Bearer"

    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    private List<String> scopes;
}
