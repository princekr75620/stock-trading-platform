package util;

/**
 * Enterprise BCrypt password hashing adapter.
 * Wraps the standard OpenBSD jBCrypt implementation (org.mindrot.jbcrypt.BCrypt)
 * providing salt generation, Blowfish key expansion, and timing-safe verification.
 */
public class BCrypt {

    /**
     * Generate standard BCrypt salt with specified log rounds.
     */
    public static String gensalt(int logRounds) {
        return org.mindrot.jbcrypt.BCrypt.gensalt(logRounds);
    }

    /**
     * Generate standard BCrypt salt with OWASP-recommended default log rounds (12).
     */
    public static String gensalt() {
        return org.mindrot.jbcrypt.BCrypt.gensalt(12);
    }

    /**
     * Hashes plaintext password using Blowfish BCrypt algorithm.
     */
    public static String hashpw(String password, String salt) {
        return org.mindrot.jbcrypt.BCrypt.hashpw(password, salt);
    }

    /**
     * Constant-time timing-safe verification of plaintext candidate against stored hash.
     */
    public static boolean checkpw(String plaintext, String hashed) {
        if (plaintext == null || hashed == null) {
            return false;
        }
        if (!hashed.startsWith("$2a$") && !hashed.startsWith("$2b$") && !hashed.startsWith("$2y$")) {
            return false;
        }
        try {
            return org.mindrot.jbcrypt.BCrypt.checkpw(plaintext, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}
