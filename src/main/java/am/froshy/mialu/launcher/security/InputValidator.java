package am.froshy.mialu.launcher.security;

import java.util.regex.Pattern;

/**
 * Validador de entrada para prevenir inyecciones y ataques.
 */
public final class InputValidator {
    
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://[a-zA-Z0-9./?=_%:-]*$"
    );
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_]{3,32}$"
    );
    
    /**
     * Valida entrada según el tipo especificado.
     */
    public boolean validate(String input, String inputType) {
        if (input == null || input.isBlank()) {
            return false;
        }
        
        return switch (inputType.toLowerCase()) {
            case "username" -> validateUsername(input);
            case "email" -> validateEmail(input);
            case "url" -> validateUrl(input);
            case "token" -> validateToken(input);
            case "safe_string" -> validateSafeString(input);
            default -> false;
        };
    }
    
    /**
     * Valida un nombre de usuario.
     */
    private boolean validateUsername(String username) {
        if (username.length() < 3 || username.length() > 32) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Valida un email.
     */
    private boolean validateEmail(String email) {
        if (email.length() > 254) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Valida una URL.
     */
    private boolean validateUrl(String url) {
        if (url.length() > 2048) {
            return false;
        }
        return URL_PATTERN.matcher(url).matches();
    }
    
    /**
     * Valida un token (JWT-like).
     */
    private boolean validateToken(String token) {
        if (token.length() < 50 || token.length() > 10000) {
            return false;
        }
        return token.matches("^[A-Za-z0-9._-]+$");
    }
    
    /**
     * Valida una cadena segura.
     */
    private boolean validateSafeString(String str) {
        if (str.length() > 1000) {
            return false;
        }
        return SAFE_STRING_PATTERN.matcher(str).matches();
    }
    
    /**
     * Sanitiza una cadena de entrada (XSS protection).
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }
        
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }
    
    /**
     * Elimina caracteres peligrosos de una cadena.
     */
    public String removeInvalidCharacters(String input) {
        if (input == null) {
            return "";
        }
        
        return input.replaceAll("[^a-zA-Z0-9._-]", "");
    }
}

