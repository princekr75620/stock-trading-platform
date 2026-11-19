package util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Enterprise Password Security Service.
 * Provides BCrypt password hashing (work factor 12) and timing-safe verification.
 * Plaintext fallback is strictly prohibited in standard verifyPassword(); legacy plaintext
 * migration is isolated behind an explicit LEGACY_PASSWORD_PREFIX marker.
 */
public class PasswordHasher {

    // Default work factor (log rounds) recommended by OWASP
    private static final int DEFAULT_ROUNDS = 12;

    // Explicit marker identifying legacy records eligible for one-time login migration
    public static final String LEGACY_PASSWORD_PREFIX = "$LEGACY$";

    /**
     * Hashes a plaintext password using BCrypt with salt rounds = 12.
     *
     * @param plaintextPassword the raw password to hash
     * @return 60-character $2a$ BCrypt hash string
     */
    public static String hashPassword(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        String salt = BCrypt.gensalt(DEFAULT_ROUNDS);
        return BCrypt.hashpw(plaintextPassword, salt);
    }

    /**
     * Checks if a password string is already in standard BCrypt hash format.
     */
    public static boolean isBCryptHash(String passwordCandidate) {
        if (passwordCandidate == null) return false;
        return passwordCandidate.startsWith("$2a$") ||
               passwordCandidate.startsWith("$2b$") ||
               passwordCandidate.startsWith("$2y$");
    }

    /**
     * Verifies a candidate password strictly against a stored BCrypt hash.
     * Rejects any non-BCrypt stored value (including raw plaintext in DB).
     *
     * @param candidatePassword candidate password provided during login
     * @param storedPassword    stored BCrypt hash from the database
     * @return true ONLY if storedPassword is a valid BCrypt hash and matches candidatePassword
     */
    public static boolean verifyPassword(String candidatePassword, String storedPassword) {
        if (candidatePassword == null || storedPassword == null || !isBCryptHash(storedPassword)) {
            return false;
        }
        try {
            return BCrypt.checkpw(candidatePassword, storedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if a stored password is explicitly tagged as a legacy pre-BCrypt record
     * eligible for temporary migration.
     */
    public static boolean isLegacyMarkedPassword(String storedPassword) {
        return storedPassword != null &&
               storedPassword.startsWith(LEGACY_PASSWORD_PREFIX) &&
               storedPassword.length() > LEGACY_PASSWORD_PREFIX.length();
    }

    /**
     * Isolated temporary verification method for explicitly tagged legacy records ONLY.
     * Uses constant-time byte comparison after verifying LEGACY_PASSWORD_PREFIX.
     */
    public static boolean verifyLegacyPassword(String candidatePassword, String storedPassword) {
        if (candidatePassword == null || !isLegacyMarkedPassword(storedPassword)) {
            return false;
        }
        String legacySecret = storedPassword.substring(LEGACY_PASSWORD_PREFIX.length());
        return MessageDigest.isEqual(
                candidatePassword.getBytes(StandardCharsets.UTF_8),
                legacySecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Formats a legacy password value with the explicit migration marker.
     */
    public static String markLegacyPassword(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Legacy password cannot be null or empty.");
        }
        return LEGACY_PASSWORD_PREFIX + plaintextPassword;
    }

    /**
     * Returns true ONLY for records explicitly marked as legacy needing re-hash.
     */
    public static boolean needsRehash(String storedPassword) {
        return isLegacyMarkedPassword(storedPassword);
    }

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    /**
     * Enforces minimum password complexity policy:
     * - 8 to 128 characters, no leading/trailing whitespace
     * - At least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special character
     * - Rejects common weak credentials
     *
     * @return null if strong enough, or a clear validation error message otherwise.
     */
    public static String validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required.";
        }
        if (!password.equals(password.trim())) {
            return "Password must not start or end with spaces.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password must not exceed " + MAX_PASSWORD_LENGTH + " characters.";
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c)) hasSpecial = true;
        }
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            return "Password must include at least one uppercase letter, lowercase letter, digit, and special character.";
        }
        String lower = password.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("password") || lower.contains("admin123") || lower.contains("qwerty") || lower.contains("12345678")) {
            return "Password is too predictable; please choose a stronger credential.";
        }
        return null;
    }
}
