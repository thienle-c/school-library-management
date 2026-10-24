package thuvien.server.security;

import java.security.SecureRandom;

/**
 * Cryptographically secure random password generator using SecureRandom.
 * Generates high-entropy temporary passwords containing uppercase, lowercase,
 * digits, and special characters.
 */
public class SecurePasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "@#$%^&*!~?_=";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a secure random temporary password with a minimum length of 16 characters.
     *
     * @param length Desired password length (must be >= 16)
     * @return Generated high-entropy password string
     */
    public static String generate(int length) {
        int finalLength = Math.max(length, 16);
        char[] password = new char[finalLength];

        // Ensure at least 2 characters from each category
        int index = 0;
        for (int i = 0; i < 2; i++) password[index++] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
        for (int i = 0; i < 2; i++) password[index++] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
        for (int i = 0; i < 2; i++) password[index++] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 0; i < 2; i++) password[index++] = SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length()));

        // Fill remaining slots from combined character pool
        while (index < finalLength) {
            password[index++] = ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length()));
        }

        // Fisher-Yates shuffle using SecureRandom
        for (int i = finalLength - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }

    public static String generateDefault() {
        return generate(16);
    }
}
