package util;

/**
 * Enterprise Password Security Service.
 * Provides BCrypt password hashing (work factor 12), timing-safe verification,
 * and seamless transparent migration from legacy plaintext passwords.
 */
public class PasswordHasher {

    // Default work factor (log rounds) recommended by OWASP
    private static final int DEFAULT_ROUNDS = 12;

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
     * Verifies a candidate password against a stored password.
     * If the stored password is a BCrypt hash, performs timing-safe BCrypt check.
     * If the stored password is a legacy plaintext password, performs comparison to allow safe migration.
     *
     * @param candidatePassword candidate password provided during login
     * @param storedPassword    stored password from the database or record
     * @return true if candidate matches stored password; false otherwise
     */
    public static boolean verifyPassword(String candidatePassword, String storedPassword) {
        if (candidatePassword == null || storedPassword == null) {
            return false;
        }

        if (isBCryptHash(storedPassword)) {
            return BCrypt.checkpw(candidatePassword, storedPassword);
        }

        // Safe migration fallback: compare against legacy plaintext
        return candidatePassword.equals(storedPassword);
    }

    /**
     * Determines whether the user record requires password migration (i.e. is not yet BCrypt-hashed).
     */
    public static boolean needsRehash(String storedPassword) {
        return !isBCryptHash(storedPassword);
    }
}
