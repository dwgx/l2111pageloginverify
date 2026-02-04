package CNM.dwgx.l2111pageverify;

import java.util.UUID;

public record UserRecord(
        UUID uuid,
        String account,
        String password,
        String salt,
        PasswordMode mode,
        String lastLoginIp,
        long lastLoginAt,
        String lastLoginSalt
) {
}
