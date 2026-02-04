package CNM.dwgx.l2111pageverify;

import java.util.Locale;

public enum PasswordMode {
    HASHED,
    PLAINTEXT;

    public static PasswordMode fromInput(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "HASH", "HASHED", "ENCRYPT", "ENCRYPTED" -> HASHED;
            case "PLAIN", "PLAINTEXT", "TEXT" -> PLAINTEXT;
            default -> null;
        };
    }
}
