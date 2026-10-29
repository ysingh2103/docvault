package com.docvault;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing and the signed-in user, kept in one place. */
final class Auth {

    /** The signed-in user, stored in the session. */
    record User(int id, String username, String role) {
        boolean isAdmin() {
            return "admin".equals(role);
        }

        /** Admins and editors may upload; viewers may only read what is shared with them. */
        boolean canUpload() {
            return "admin".equals(role) || "editor".equals(role);
        }
    }

    static final String SESSION_KEY = "docvault.user";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Checked when the username does not exist, so a miss takes as long as a hit. */
    static final String DUMMY_HASH = hash("not-a-real-password");

    private Auth() {}

    /** Format: pbkdf2$iterations$salt$hash (salt and hash in Base64). */
    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] h = pbkdf2(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(h);
    }

    static boolean verify(String password, String stored) {
        if (stored == null) {
            return false;
        }
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !"pbkdf2".equals(p[0])) {
                return false;
            }
            int iterations = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(password, salt, iterations, KEY_BITS)).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        }
    }

    /** The signed-in user, or null. Does not create a session. */
    static User current(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (User) s.getAttribute(SESSION_KEY);
    }
}
