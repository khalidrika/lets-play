package com.letsplay.api.service;

import com.letsplay.api.dto.LoginRequest;
import com.letsplay.api.dto.RegisterRequest;
import com.letsplay.api.exception.ApiException;
import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;
import com.letsplay.api.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtEncoder jwtEncoder;
    AuthService service;

    @BeforeEach
    void setup() { service = new AuthService(users, encoder, jwtEncoder, "lets-play-api", 30); }

    @Test
    void registrationAlwaysCreatesUserRoleAndHashesPassword() {
        when(encoder.encode("VerySecret123")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("id-123");
            return user;
        });
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                new Jwt("fake.jwt.token", Instant.now(), Instant.now().plusSeconds(1800),
                        java.util.Map.of("alg", "HS256"), java.util.Map.of("sub", "id-123")));

        var response = service.register(new RegisterRequest("Khalid", " TEST@EXAMPLE.COM ", "VerySecret123"));
        assertEquals("fake.jwt.token", response.accessToken());
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals(Role.USER, captured.getValue().getRole());
        assertEquals("test@example.com", captured.getValue().getEmail());
        assertEquals("bcrypt-hash", captured.getValue().getPassword());
    }

    @Test
    void wrongPasswordIs401AndDoesNotCreateToken() {
        User user = new User("User", "user@example.com", "stored-hash", Role.USER);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () ->
                service.login(new LoginRequest("user@example.com", "wrong-password")));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verifyNoInteractions(jwtEncoder);
    }
}
