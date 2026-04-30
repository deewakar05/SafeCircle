package com.safecircle.service;

import com.safecircle.dto.AuthRequest.AuthResponse;
import com.safecircle.dto.AuthRequest.LoginRequest;
import com.safecircle.dto.AuthRequest.SignupRequest;
import com.safecircle.model.User;
import com.safecircle.repository.UserRepository;
import com.safecircle.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository       userRepository;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private JwtUtil              jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-123")
                .name("Alice")
                .email("alice@example.com")
                .password("encoded-password")
                .role("MEMBER")
                .build();
    }

    // ── signup ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("signup: new user is saved and JWT returned")
    void testSignup_success() {
        SignupRequest req = new SignupRequest("Alice", "alice@example.com", "secret123");

        when(userRepository.existsByEmail(req.email())).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateToken(testUser.getEmail())).thenReturn("jwt-token");

        AuthResponse res = authService.signup(req);

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.userId()).isEqualTo("user-123");
        assertThat(res.name()).isEqualTo("Alice");
        assertThat(res.email()).isEqualTo("alice@example.com");

        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("secret123");
    }

    @Test
    @DisplayName("signup: duplicate email throws IllegalArgumentException")
    void testSignup_duplicateEmail() {
        SignupRequest req = new SignupRequest("Alice", "alice@example.com", "secret123");

        when(userRepository.existsByEmail(req.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: valid credentials return JWT")
    void testLogin_success() {
        LoginRequest req = new LoginRequest("alice@example.com", "secret123");

        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken(testUser.getEmail())).thenReturn("jwt-token");

        AuthResponse res = authService.login(req);

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.userId()).isEqualTo("user-123");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login: wrong password propagates BadCredentialsException")
    void testLogin_badCredentials() {
        LoginRequest req = new LoginRequest("alice@example.com", "wrong-password");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtUtil, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("login: user not in DB after auth throws IllegalArgumentException")
    void testLogin_userNotFound() {
        LoginRequest req = new LoginRequest("ghost@example.com", "pass");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }
}
