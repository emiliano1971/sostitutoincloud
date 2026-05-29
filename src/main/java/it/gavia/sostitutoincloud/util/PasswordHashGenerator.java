package it.gavia.sostitutoincloud.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = args.length > 0 ? args[0] : "atena";
        String hash = encoder.encode(password);
        System.out.println("Password: " + password);
        System.out.println("Hash BCrypt: " + hash);
        System.out.println();
        System.out.println("SQL:");
        System.out.println("UPDATE utente SET password_hash = '"
                + hash + "' WHERE password_hash = '{CHANGE_ME}';");
    }
}
