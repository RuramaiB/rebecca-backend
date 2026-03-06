package com.example.taxbackend.auth;

import com.example.taxbackend.config.JwtService;
import com.example.taxbackend.exception.ResourceNotFoundException;
import com.example.taxbackend.token.Token;
import com.example.taxbackend.token.TokenRepository;
import com.example.taxbackend.token.TokenState;
import com.example.taxbackend.token.TokenType;
import com.example.taxbackend.user.Role;
import com.example.taxbackend.user.User;
import com.example.taxbackend.user.UserRepository;
import com.example.taxbackend.verification.Verification;
import com.example.taxbackend.verification.VerificationRepository;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.repository.ArtistRepository;
import com.example.taxbackend.service.TaxService;
import com.example.taxbackend.enums.AuthStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
        private final UserRepository repository;
        private final TokenRepository tokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;

        private final AuthenticationManager authenticationManager;
        private final VerificationRepository verificationRepository;
        private final UserRepository userRepository;
        private final ArtistRepository artistRepository;
        private final TaxService taxService;

        public AuthenticationResponse register(RegisterRequest request) {
                if (existsByEmail(request.getEmail())) {
                        System.out.println("User already exists.");
                        return AuthenticationResponse.builder().message("User already exists").build();
                } else {
                        var user = User.builder()
                                        .firstname(request.getFirstname())
                                        .lastname(request.getLastname())
                                        .email(request.getEmail())
                                        .physicalAddress(request.getPhysicalAddress())
                                        .gender(request.getGender())
                                        .dateOfBirth(request.getDateOfBirth())
                                        .password(passwordEncoder.encode(request.getPassword()))
                                        .role(request.getRole())
                                        .enabled(true)
                                        .build();
                        var savedUser = repository.save(user);

                        // 🔹 ADD TO DIGITAL SERVICE TAX (DST) IF ARTIST
                        if (user.getRole() == Role.ARTIST) {
                                Artist artist = Artist.builder()
                                                .email(user.getEmail())
                                                .name(user.getFirstname() + " " + user.getLastname())
                                                .role(Role.ARTIST)
                                                .authStatus(AuthStatus.AUTHENTICATED) // Assuming they are authenticated
                                                                                      // after registration
                                                .build();
                                artist = artistRepository.save(artist);
                                taxService.updateComplianceStatus(artist.getId());
                        }

                        var jwtToken = jwtService.generateToken(user);
                        var refreshToken = jwtService.generateRefreshToken(user);
                        saveUserToken(savedUser, jwtToken);
                        return AuthenticationResponse.builder()
                                        .role(user.getRole())
                                        .email(user.getEmail())
                                        .accessToken(jwtToken)
                                        .refreshToken(refreshToken)
                                        .message("Account created successfully")
                                        .build();
                }
        }

        public String generateVerificationCode(String studentEmail) {
                User user = repository.findByEmail(studentEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                int[] digits = new int[6];
                String code = "";
                for (int i = 0; i < 6; i++) {
                        digits[i] = (int) (Math.random() * 10);
                }
                for (int count : digits)
                        code += count;
                // Verification ver= verificationRepository.findByEmail(studentEmail)
                // .orElse(new Verification());
                // verificationRepository.delete(verificationRepository.findByEmail(studentEmail).orElseThrow(()->
                // new ResourceNotFoundException("Verification not found.")));
                Verification verification = new Verification();
                verification.setEmail(user.getEmail());
                verification.setVerificationCode(code);
                verificationRepository.insert(verification);
                return code;
        }

        public ResponseEntity<String> verifyCode(String email, String code) {
                Verification verification = verificationRepository.findByEmailAndVerificationCode(email, code)
                                .orElseThrow(() -> new ResourceNotFoundException("Verification details not found"));
                if (Objects.equals(verification.getVerificationCode(), code)) {
                        User user = repository.findByEmail(email)
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                        Token tkn = tokenRepository.findByUserAndTokenState(user, TokenState.fresh)
                                        .orElseThrow(() -> new ResourceNotFoundException("Token not found"));
                        tkn.setRevoked(false);
                        tokenRepository.save(tkn);
                        // user.setIsVerified(true);
                        // user.setIsFirstTime(false);
                        repository.save(user);
                        return ResponseEntity.ok("Verification successful.");
                } else {
                        return ResponseEntity.ok("Verification failed.");
                }
        }

        public AuthenticationResponse authenticate(AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));
                var user = repository.findByEmail(request.getEmail())
                                .orElseThrow();
                var jwtToken = jwtService.generateToken(user);
                var userRole = user.getRole();
                var email = user.getEmail();
                var refreshToken = jwtService.generateRefreshToken(user);
                saveUserToken(user, jwtToken);
                Token tkn = tokenRepository.findByUserAndTokenState(user, TokenState.fresh)
                                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));
                tkn.setRevoked(false);
                tokenRepository.save(tkn);

                return AuthenticationResponse.builder()
                                .role(userRole)
                                .email(email)
                                .accessToken(jwtToken)
                                .message("Login successful.")
                                // .isFirstTime(user.getIsFirstTime())
                                // .isVerified(user.getIsVerified())
                                // .hasTwoFactor(user.getHasTwoFactor())
                                .refreshToken(refreshToken)
                                .build();

        }

        public AuthenticationResponse authenticateWithVerification(AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));
                var user = repository.findByEmail(request.getEmail())
                                .orElseThrow();
                var jwtToken = jwtService.generateToken(user);
                var userRole = user.getRole();
                var email = user.getEmail();
                var refreshToken = jwtService.generateRefreshToken(user);
                // try {
                String code = generateVerificationCode(user.getEmail());
                System.out.println("Verification code: " + code);
                // emailSender.send(
                // request.getEmail(),
                // buildEmail(user.getFirstname() + " " +user.getLastname(),
                // code));
                // }catch (Exception e){
                // return AuthenticationResponse.builder().message("Verification request failed.
                // Try again later.").build();
                // }
                saveUserToken(user, jwtToken);
                return AuthenticationResponse.builder()
                                .role(userRole)
                                .email(email)
                                .accessToken(jwtToken)
                                .message("Check your email for verification code")
                                .refreshToken(refreshToken)
                                .build();
        }

        private void saveUserToken(User user, String jwtToken) {
                List<Token> tokens = tokenRepository.findByUser(user);
                for (Token t : tokens) {
                        t.setTokenState(TokenState.old);
                        tokenRepository.save(t);
                }
                var token = Token.builder()
                                .user(user)
                                .token(jwtToken)
                                .tokenType(TokenType.BEARER)
                                .expired(false)
                                .revoked(true)
                                .tokenState(TokenState.fresh)
                                .build();
                tokenRepository.save(token);
        }

        private void revokeAllUserTokens(User user) {
                var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
                if (validUserTokens.isEmpty())
                        return;
                validUserTokens.forEach(token -> {
                        token.setExpired(true);
                        token.setRevoked(true);
                });
                tokenRepository.saveAll(validUserTokens);
        }

        public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                final String refreshToken;
                final String userEmail;
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        return;
                }
                refreshToken = authHeader.substring(7);
                userEmail = jwtService.extractUsername(refreshToken);
                if (userEmail != null) {
                        var user = this.repository.findByEmail(userEmail)
                                        .orElseThrow();
                        if (jwtService.isTokenValid(refreshToken, user)) {
                                var accessToken = jwtService.generateToken(user);
                                // revokeAllUserTokens(user);
                                saveUserToken(user, accessToken);
                                var authResponse = AuthenticationResponse.builder()
                                                .accessToken(accessToken)
                                                .refreshToken(refreshToken)
                                                .build();
                                new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
                        }
                }
        }

        public boolean existsByEmail(String email) {
                return repository.existsByEmail(email);
        }

        private String buildEmail(String name, String verificationCode) {
                return "<div style=\"font-family:Helvetica,Arial,sans-serif;font-size:16px;margin:0;color:#0b0c0c\">\n"
                                +
                                "\n" +
                                "<span style=\"display:none;font-size:1px;color:#fff;max-height:0\"></span>\n" +
                                "\n" +
                                "  <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;min-width:100%;width:100%!important\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n"
                                +
                                "    <tbody><tr>\n" +
                                "      <td width=\"100%\" height=\"53\" bgcolor=\"#0b0c0c\">\n" +
                                "        \n" +
                                "        <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;max-width:580px\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\">\n"
                                +
                                "          <tbody><tr>\n" +
                                "            <td width=\"70\" bgcolor=\"#0b0c0c\" valign=\"middle\">\n" +
                                "                <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n"
                                +
                                "                  <tbody><tr>\n" +
                                "                    <td style=\"padding-left:10px\">\n" +
                                "                  \n" +
                                "                    </td>\n" +
                                "                    <td style=\"font-size:28px;line-height:1.315789474;Margin-top:4px;padding-left:10px\">\n"
                                +
                                "                      <span style=\"font-family:Helvetica,Arial,sans-serif;font-weight:700;color:#ffffff;text-decoration:none;vertical-align:top;display:inline-block\">Confirm your email</span>\n"
                                +
                                "                    </td>\n" +
                                "                  </tr>\n" +
                                "                </tbody></table>\n" +
                                "              </a>\n" +
                                "            </td>\n" +
                                "          </tr>\n" +
                                "        </tbody></table>\n" +
                                "        \n" +
                                "      </td>\n" +
                                "    </tr>\n" +
                                "  </tbody></table>\n" +
                                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n"
                                +
                                "    <tbody><tr>\n" +
                                "      <td width=\"10\" height=\"10\" valign=\"middle\"></td>\n" +
                                "      <td>\n" +
                                "        \n" +
                                "                <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n"
                                +
                                "                  <tbody><tr>\n" +
                                "                    <td width=\"100%\" height=\"10\"></td>\n" +
                                "                  </tr>\n" +
                                "                </tbody></table>\n" +
                                "        \n" +
                                "      </td>\n" +
                                "      <td width=\"10\" valign=\"middle\" height=\"10\"></td>\n" +
                                "    </tr>\n" +
                                "  </tbody></table>\n" +
                                "\n" +
                                "\n" +
                                "\n" +
                                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n"
                                +
                                "    <tbody><tr>\n" +
                                "      <td height=\"30\"><br></td>\n" +
                                "    </tr>\n" +
                                "    <tr>\n" +
                                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                                "      <td style=\"font-family:Helvetica,Arial,sans-serif;font-size:19px;line-height:1.315789474;max-width:560px\">\n"
                                +
                                "        \n" +
                                "            <p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\">Hi "
                                + name
                                + ",</p><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> Thank you for registering. Please use the following verification code to activate your account: </p><blockquote style=\"Margin:0 0 20px 0;border-left:10px solid #b1b4b6;padding:15px 0 0.1px 15px;font-size:19px;line-height:25px\"><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> <a href=\""
                                + verificationCode + "\">Activate Now</a> </p></blockquote>\n <p>See you soon</p>" +
                                "        \n" +
                                "      </td>\n" +
                                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                                "    </tr>\n" +
                                "    <tr>\n" +
                                "      <td height=\"30\"><br></td>\n" +
                                "    </tr>\n" +
                                "  </tbody></table><div class=\"yj6qo\"></div><div class=\"adL\">\n" +
                                "\n" +
                                "</div></div>";
        }

        public User getUserByEmail(String email) {
                return repository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        public List<User> getAllUsersByRole(Role role) {
                return userRepository.findAllByRole(role);
        }

        public String buildNotification(String heading, String msg, String sender) {
                return "<div style=\"font-family:Helvetica,Arial,sans-serif;font-size:16px;margin:0;color:#0b0c0c\">\n"
                                +
                                "  <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;min-width:100%;width:100%!important\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n"
                                +
                                "    <tbody><tr>\n" +
                                "      <td width=\"100%\" height=\"53\" bgcolor=\"#0b0c0c\">\n" +
                                "        <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;max-width:580px\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\">\n"
                                +
                                "          <tbody><tr>\n" +
                                "            <td style=\"font-size:24px;font-weight:bold;color:#ffffff;padding:15px;text-align:center;\">\n"
                                +
                                "              " + heading + "\n" +
                                "            </td>\n" +
                                "          </tr>\n" +
                                "        </tbody></table>\n" +
                                "      </td>\n" +
                                "    </tr>\n" +
                                "  </tbody></table>\n" +
                                "\n" +
                                "  <table role=\"presentation\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important;margin-top:20px;\" width=\"100%\">\n"
                                +
                                "    <tbody><tr>\n" +
                                "      <td style=\"font-family:Helvetica,Arial,sans-serif;font-size:18px;line-height:1.5;color:#0b0c0c;padding:20px;background-color:#f9f9f9;border-radius:8px;\">\n"
                                +
                                "        <p style=\"margin:0 0 15px 0;\">" + msg + "</p>\n" +
                                "        <p style=\"margin:0;font-size:14px;color:#555555;\">— " + sender + "</p>\n" +
                                "      </td>\n" +
                                "    </tr>\n" +
                                "  </tbody></table>\n" +
                                "</div>";
        }

        public List<User> getAllUsers() {
                return repository.findAll();
        }

        public ResponseEntity<AuthenticationResponse> updateUserRole(String email, Role role) {
                User user = repository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                user.setRole(role);
                repository.save(user);
                return ResponseEntity.ok(AuthenticationResponse
                                .builder()
                                .message("User role updated successfully")
                                .build());
        }
}
