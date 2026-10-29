package com.letsplay.api.service;

import com.letsplay.api.dto.LoginRequest;
import com.letsplay.api.dto.RegisterRequest;
import com.letsplay.api.dto.TokenResponse;
import com.letsplay.api.exception.ApiException;
import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;
import com.letsplay.api.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long expirationMinutes;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, @Value("${app.jwt.issuer}") String issuer,
                       @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
        if (expirationMinutes < 1 || expirationMinutes > 1440) {
            throw new IllegalArgumentException("JWT lifetime must be between 1 and 1440 minutes");
        }
    }

    public TokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = users.save(new User(request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.USER));
        return tokenFor(user);
    }

    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email).orElse(null);
        // Same response for unknown email and wrong password (no account enumeration).
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return tokenFor(user);
    }

    private TokenResponse tokenFor(User user) {
        Instant now = Instant.now();
        Duration lifetime = Duration.ofMinutes(expirationMinutes);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", lifetime.toSeconds());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
