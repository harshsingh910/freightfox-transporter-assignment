package com.freightfox.auth;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates passwords against production security requirements.
 */
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    private static final Set<String> COMMON_DENYLIST = Set.of(
            "password123456!",
            "adminpassword1!",
            "qwerty123456!",
            "letmein123456!",
            "welcome123456!",
            "freightfox123!",
            "changeme12345!"
    );

    public record ValidationResult(boolean valid, List<String> errorMessages) {}

    public ValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null) {
            errors.add("Password must not be null.");
            return new ValidationResult(false, errors);
        }

        if (password.length() < MIN_LENGTH) {
            errors.add("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.length() > MAX_LENGTH) {
            errors.add("Password must not exceed " + MAX_LENGTH + " characters.");
        }
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter (A-Z).");
        }
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter (a-z).");
        }
        if (!DIGIT_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one digit (0-9).");
        }
        if (!SPECIAL_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one special character.");
        }

        if (COMMON_DENYLIST.contains(password.toLowerCase())) {
            errors.add("Password is too common or easily guessed. Please choose a more complex phrase.");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
}
