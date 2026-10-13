package com.freightfox.auth;

import com.freightfox.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Production Secret Configuration Regression Tests")
class SecretConfigurationRegressionTest {

    private Environment mockProdEnvironment() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        return env;
    }

    private Environment mockLocalEnvironment() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"local"});
        return env;
    }

    // ==========================================
    // 1. JWT Secret Production Validation Tests
    // ==========================================

    @Test
    @DisplayName("Production: Should fail startup when JWT_SECRET is missing or blank")
    void testMissingJwtSecretInProductionThrows() {
        Environment prodEnv = mockProdEnvironment();

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("", "freightfox-platform", 3600, prodEnv)
        );
        assertTrue(ex1.getMessage().contains("requires a valid, explicit 'JWT_SECRET'"));
        assertFalse(ex1.getMessage().contains("404E6352")); // Never expose secrets in errors

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("   ", "freightfox-platform", 3600, prodEnv)
        );
        assertTrue(ex2.getMessage().contains("requires a valid, explicit 'JWT_SECRET'"));
    }

    @Test
    @DisplayName("Production: Should fail startup when JWT_SECRET equals known insecure default")
    void testDefaultJwtSecretInProductionThrows() {
        Environment prodEnv = mockProdEnvironment();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(JwtTokenProvider.INSECURE_DEFAULT_SECRET, "freightfox-platform", 3600, prodEnv)
        );
        assertTrue(ex.getMessage().contains("must not use the publicly known default JWT secret"));
    }

    @Test
    @DisplayName("Production: Should fail startup when JWT_SECRET is shorter than 32 bytes (256 bits)")
    void testShortJwtSecretInProductionThrows() {
        Environment prodEnv = mockProdEnvironment();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("too-short-secret-under-32-chars", "freightfox-platform", 3600, prodEnv)
        );
        assertTrue(ex.getMessage().contains("at least 32 bytes (256 bits)"));
    }

    @Test
    @DisplayName("Production: Should successfully initialize with valid, secure 32+ byte JWT secret")
    void testValidJwtSecretInProductionSucceeds() {
        Environment prodEnv = mockProdEnvironment();
        String secureProdSecret = "VerySecureProductionKeyForSigningJwtTokensWithHighEntropy2026!#$";

        JwtTokenProvider provider = new JwtTokenProvider(secureProdSecret, "freightfox-prod", 3600, prodEnv);
        assertNotNull(provider);

        String token = provider.generateToken("admin", "ROLE_ADMIN");
        assertTrue(provider.validateToken(token));
        assertEquals("admin", provider.getUsernameFromToken(token));
        assertEquals("ROLE_ADMIN", provider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Non-Production: Should allow safe fallback default in local development")
    void testNonProductionJwtSecretFallback() {
        Environment localEnv = mockLocalEnvironment();

        JwtTokenProvider provider = new JwtTokenProvider("", "freightfox-local", 3600, localEnv);
        assertNotNull(provider);

        String token = provider.generateToken("devuser", "ROLE_USER");
        assertTrue(provider.validateToken(token));
        assertEquals("devuser", provider.getUsernameFromToken(token));
    }

    // ====================================================
    // 2. Bootstrap Admin Password Validation Tests
    // ====================================================

    @Test
    @DisplayName("Production: Should fail startup when BOOTSTRAP_ADMIN_PASSWORD is missing and admin does not exist")
    void testMissingBootstrapAdminPasswordInProductionThrows() {
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.existsByUsername("admin")).thenReturn(false);
        when(userRepo.existsByEmail(anyString())).thenReturn(false);

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordValidator validator = new PasswordValidator();
        JwtTokenProvider jwtProvider = mock(JwtTokenProvider.class);
        Environment prodEnv = mockProdEnvironment();

        AuthService authService = new AuthService(userRepo, encoder, validator, jwtProvider, prodEnv);
        authService.setBootstrapAdminProperties(true, "admin", "admin@freightfox.com", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, authService::initBootstrapAdmin);
        assertTrue(ex.getMessage().contains("requires a valid, explicit 'BOOTSTRAP_ADMIN_PASSWORD'"));
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("Production: Should fail startup when BOOTSTRAP_ADMIN_PASSWORD equals known insecure default")
    void testDefaultBootstrapAdminPasswordInProductionThrows() {
        UserRepository userRepo = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordValidator validator = new PasswordValidator();
        JwtTokenProvider jwtProvider = mock(JwtTokenProvider.class);
        Environment prodEnv = mockProdEnvironment();

        AuthService authService = new AuthService(userRepo, encoder, validator, jwtProvider, prodEnv);
        authService.setBootstrapAdminProperties(true, "admin", "admin@freightfox.com", AuthService.INSECURE_DEFAULT_ADMIN_PASSWORD);

        IllegalStateException ex = assertThrows(IllegalStateException.class, authService::initBootstrapAdmin);
        assertTrue(ex.getMessage().contains("must not use the publicly known default bootstrap administrator password"));
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("Production: Should fail startup when BOOTSTRAP_ADMIN_PASSWORD violates password complexity policy")
    void testWeakBootstrapAdminPasswordInProductionThrows() {
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.existsByUsername("admin")).thenReturn(false);
        when(userRepo.existsByEmail(anyString())).thenReturn(false);

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordValidator validator = new PasswordValidator();
        JwtTokenProvider jwtProvider = mock(JwtTokenProvider.class);
        Environment prodEnv = mockProdEnvironment();

        AuthService authService = new AuthService(userRepo, encoder, validator, jwtProvider, prodEnv);
        authService.setBootstrapAdminProperties(true, "admin", "admin@freightfox.com", "weakpass");

        IllegalStateException ex = assertThrows(IllegalStateException.class, authService::initBootstrapAdmin);
        assertTrue(ex.getMessage().contains("password policy requirements"));
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("Production: Should successfully bootstrap admin with strong, explicit password")
    void testValidBootstrapAdminPasswordInProductionSucceeds() {
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.existsByUsername("admin")).thenReturn(false);
        when(userRepo.existsByEmail(anyString())).thenReturn(false);

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hashed_secure_pass");

        PasswordValidator validator = new PasswordValidator();
        JwtTokenProvider jwtProvider = mock(JwtTokenProvider.class);
        Environment prodEnv = mockProdEnvironment();

        AuthService authService = new AuthService(userRepo, encoder, validator, jwtProvider, prodEnv);
        authService.setBootstrapAdminProperties(true, "admin", "admin@freightfox.com", "SuperSecure@ProdAdmin2026#Valid");

        assertDoesNotThrow(authService::initBootstrapAdmin);
        verify(userRepo, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Existing Admin: Should NEVER reset password or privileges if administrator already exists")
    void testExistingAdminAccountPreservesCredentialsAndPrivileges() {
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.existsByUsername("admin")).thenReturn(true); // Account already in DB

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordValidator validator = new PasswordValidator();
        JwtTokenProvider jwtProvider = mock(JwtTokenProvider.class);
        Environment prodEnv = mockProdEnvironment();

        AuthService authService = new AuthService(userRepo, encoder, validator, jwtProvider, prodEnv);
        // Even if no password is provided on container restart, an existing admin must not be disturbed
        authService.setBootstrapAdminProperties(true, "admin", "admin@freightfox.com", "");

        assertDoesNotThrow(authService::initBootstrapAdmin);
        // CRITICAL: Must never attempt to update or re-save user
        verify(userRepo, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    @DisplayName("Disabled Bootstrap: Should skip provisioning when BOOTSTRAP_ADMIN_ENABLED=false")
    void testDisabledBootstrapAdminSkipsCreation() {
        UserRepository userRepo = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordValidator validator = new PasswordValidator();
        JwtTokenProvider jwtProvider = mock(JwtTokenProvider.class);
        Environment prodEnv = mockProdEnvironment();

        AuthService authService = new AuthService(userRepo, encoder, validator, jwtProvider, prodEnv);
        authService.setBootstrapAdminProperties(false, "admin", "admin@freightfox.com", "");

        assertDoesNotThrow(authService::initBootstrapAdmin);
        verify(userRepo, never()).existsByUsername(anyString());
        verify(userRepo, never()).save(any());
    }
}
