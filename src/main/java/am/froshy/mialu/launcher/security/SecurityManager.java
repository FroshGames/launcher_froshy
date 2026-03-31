package am.froshy.mialu.launcher.security;

import java.net.http.HttpClient;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Gestor centralizado de seguridad para el launcher.
 * Implementa múltiples medidas de protección contra ataques y vulnerabilidades.
 */
public final class SecurityManager {
    private static final Logger LOGGER = Logger.getLogger(SecurityManager.class.getName());
    
    // Configuración de seguridad
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutos
    private static final int MAX_TOKEN_SIZE = 10_000; // bytes
    private static final int MAX_REDIRECT_URI_LENGTH = 2048;
    private static final long MAX_REQUEST_SIZE = 1_000_000; // 1 MB
    
    // Puertos permitidos
    private static final Set<Integer> ALLOWED_PORTS = new HashSet<>(Arrays.asList(
            3000, 7878, 8080, 8443
    ));
    
    // Hosts permitidos
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "[::1]"
    ));
    
    private final SecurityAuditLogger auditLogger;
    private final RateLimiter rateLimiter;
    private final InputValidator inputValidator;
    
    public SecurityManager(Path logDirectory) {
        this.auditLogger = new SecurityAuditLogger(logDirectory);
        this.rateLimiter = new RateLimiter(MAX_LOGIN_ATTEMPTS, LOGIN_LOCKOUT_DURATION_MS);
        this.inputValidator = new InputValidator();
    }
    
    /**
     * Valida un redirect_uri según las políticas de seguridad.
     */
    public boolean validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "FAILED", "Redirect URI null or empty");
            return false;
        }
        
        if (redirectUri.length() > MAX_REDIRECT_URI_LENGTH) {
            auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "FAILED", 
                    "Redirect URI too long: " + redirectUri.length());
            return false;
        }
        
        try {
            URI uri = URI.create(redirectUri);
            
            // Solo permite http/https
            if (!uri.getScheme().matches("https?")) {
                auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "FAILED", 
                        "Invalid scheme: " + uri.getScheme());
                return false;
            }
            
            // Solo permite hosts locales
            String host = uri.getHost();
            if (!isAllowedHost(host)) {
                auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "FAILED", 
                        "Host not allowed: " + host);
                return false;
            }
            
            // Valida puerto
            int port = uri.getPort();
            if (port > 0 && !isAllowedPort(port)) {
                auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "FAILED", 
                        "Port not allowed: " + port);
                return false;
            }
            
            auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "SUCCESS", redirectUri);
            return true;
        } catch (Exception ex) {
            auditLogger.logSecurityEvent("REDIRECT_URI_VALIDATION", "ERROR", ex.getMessage());
            return false;
        }
    }
    
    /**
     * Valida un token según las políticas de seguridad.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        
        if (token.length() > MAX_TOKEN_SIZE) {
            auditLogger.logSecurityEvent("TOKEN_VALIDATION", "FAILED", "Token too large");
            return false;
        }
        
        // Valida que sea un token válido (JWT-like structure)
        return token.matches("^[A-Za-z0-9._-]+$");
    }
    
    /**
     * Verifica si se puede intentar login (rate limiting).
     */
    public boolean canAttemptLogin(String identifier) {
        boolean allowed = rateLimiter.canAttempt(identifier);
        if (!allowed) {
            auditLogger.logSecurityEvent("LOGIN_ATTEMPT", "BLOCKED", 
                    "Too many attempts for: " + identifier);
        }
        return allowed;
    }
    
    /**
     * Registra un intento de login exitoso.
     */
    public void recordLoginSuccess(String identifier, String username) {
        rateLimiter.recordSuccess(identifier);
        auditLogger.logSecurityEvent("LOGIN_SUCCESS", "SUCCESS", username);
    }
    
    /**
     * Registra un intento de login fallido.
     */
    public void recordLoginFailure(String identifier, String reason) {
        rateLimiter.recordAttempt(identifier);
        auditLogger.logSecurityEvent("LOGIN_ATTEMPT", "FAILED", reason);
    }
    
    /**
     * Valida que el tamaño de la solicitud sea aceptable.
     */
    public boolean validateRequestSize(long size) {
        if (size > MAX_REQUEST_SIZE) {
            auditLogger.logSecurityEvent("REQUEST_SIZE_VALIDATION", "FAILED", 
                    "Request too large: " + size);
            return false;
        }
        return true;
    }
    
    /**
     * Configura permisos seguros en un archivo de configuración.
     */
    public void setSecureFilePermissions(Path filePath) {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>();
            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.OWNER_WRITE);
            
            try {
                Files.setPosixFilePermissions(filePath, permissions);
            } catch (UnsupportedOperationException ex) {
                // Windows no soporta POSIX permissions, pero sigue siendo seguro
                LOGGER.warning("POSIX permissions not supported on this OS");
            }
            
            auditLogger.logSecurityEvent("FILE_PERMISSIONS", "SUCCESS", 
                    filePath.toString());
        } catch (Exception ex) {
            auditLogger.logSecurityEvent("FILE_PERMISSIONS", "ERROR", ex.getMessage());
        }
    }
    
    /**
     * Valida un parámetro de entrada.
     */
    public boolean validateInput(String input, String inputType) {
        return inputValidator.validate(input, inputType);
    }
    
    /**
     * Sanitiza una cadena de entrada.
     */
    public String sanitizeInput(String input) {
        return inputValidator.sanitize(input);
    }
    
    /**
     * Obtiene el logger de auditoría.
     */
    public SecurityAuditLogger getAuditLogger() {
        return auditLogger;
    }
    
    private boolean isAllowedHost(String host) {
        if (host == null) return false;
        
        // Localhost en diferentes formatos
        if (host.equals("localhost") || host.equals("127.0.0.1") || 
            host.equals("::1") || host.equals("0.0.0.0")) {
            return true;
        }
        
        // Localhost en IPv6
        if (host.equals("[::1]") || host.equals("[0:0:0:0:0:0:0:1]")) {
            return true;
        }
        
        return false;
    }
    
    private boolean isAllowedPort(int port) {
        return port > 0 && port <= 65535 && ALLOWED_PORTS.contains(port);
    }
}

