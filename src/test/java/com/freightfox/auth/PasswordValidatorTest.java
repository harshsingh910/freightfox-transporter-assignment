package com.freightfox.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordValidator Unit Tests")
class PasswordValidatorTest {

    private PasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordValidator();
    }

    @Test
    @DisplayName("Should accept strong valid passwords")
    void testValidPassword() {
        PasswordValidator.ValidationResult result = validator.validate("FreightFox@2026Safe!");
        assertTrue(result.valid());
        assertTrue(result.errorMessages().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",                         // Too short (<12 chars)
            "alllowercase1234!",             // No uppercase
            "ALLUPPERCASE1234!",             // No lowercase
            "NoDigitsInThisPassword!",       // No digits
            "NoSpecialChar123456",           // No special char
            "password123456!",               // Common denylist
            "adminpassword1!"                // Common denylist
    })
    @DisplayName("Should reject passwords violating complexity or denylist policy")
    void testInvalidPasswords(String password) {
        PasswordValidator.ValidationResult result = validator.validate(password);
        assertFalse(result.valid());
        assertFalse(result.errorMessages().isEmpty());
    }

    @Test
    @DisplayName("Should reject null password safely")
    void testNullPassword() {
        PasswordValidator.ValidationResult result = validator.validate(null);
        assertFalse(result.valid());
    }
}
