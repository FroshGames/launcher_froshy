package am.froshy.mialu.launcher.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Logger de auditoría de seguridad para tracking de eventos importantes.
 */
public final class SecurityAuditLogger {
    private static final Logger LOGGER = Logger.getLogger(SecurityAuditLogger.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final Path logDirectory;
    private final Path auditLogFile;
    
    public SecurityAuditLogger(Path logDirectory) {
        this.logDirectory = logDirectory;
        this.auditLogFile = logDirectory.resolve("security-audit.log");
        
        try {
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
            }
            if (!Files.exists(auditLogFile)) {
                Files.createFile(auditLogFile);
            }
        } catch (Exception ex) {
            LOGGER.warning("No se pudo crear archivo de auditoría: " + ex.getMessage());
        }
    }
    
    /**
     * Registra un evento de seguridad.
     */
    public void logSecurityEvent(String eventType, String status, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format("[%s] %s | Status: %s | Details: %s",
                timestamp, eventType, status, sanitizeForLog(details));
        
        // Log en archivo
        try {
            Files.writeString(
                    auditLogFile,
                    logEntry + "\n",
                    StandardOpenOption.APPEND
            );
        } catch (Exception ex) {
            LOGGER.warning("Error escribiendo en log de auditoría: " + ex.getMessage());
        }
        
        // Log en consola también
        LOGGER.info(logEntry);
    }
    
    /**
     * Registra un intento de autenticación.
     */
    public void logAuthenticationAttempt(String username, boolean success, String reason) {
        String status = success ? "SUCCESS" : "FAILED";
        logSecurityEvent("AUTHENTICATION_ATTEMPT", status, 
                "User: " + sanitizeUsername(username) + " | Reason: " + reason);
    }
    
    /**
     * Registra acceso a recursos sensibles.
     */
    public void logSensitiveResourceAccess(String resource, String user, boolean allowed) {
        String status = allowed ? "ALLOWED" : "DENIED";
        logSecurityEvent("SENSITIVE_RESOURCE_ACCESS", status,
                "Resource: " + resource + " | User: " + sanitizeUsername(user));
    }
    
    /**
     * Registra una excepción de seguridad.
     */
    public void logSecurityException(String context, Exception ex) {
        logSecurityEvent("SECURITY_EXCEPTION", "ERROR",
                "Context: " + context + " | Error: " + ex.getClass().getSimpleName() + 
                " | Message: " + ex.getMessage());
    }
    
    /**
     * Registra una violación de política.
     */
    public void logPolicyViolation(String violationType, String details) {
        logSecurityEvent("POLICY_VIOLATION", "ALERT", 
                "Type: " + violationType + " | Details: " + details);
    }
    
    private String sanitizeForLog(String input) {
        if (input == null) {
            return "";
        }
        
        // Reemplaza caracteres peligrosos
        return input
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\"", "\\\"")
                .substring(0, Math.min(input.length(), 500)); // Limita longitud
    }
    
    private String sanitizeUsername(String username) {
        if (username == null) {
            return "[UNKNOWN]";
        }
        
        // Oculta información sensible del username
        if (username.length() > 2) {
            return username.substring(0, 2) + "***";
        }
        return "***";
    }
    
    /**
     * Obtiene la ruta del archivo de auditoría.
     */
    public Path getAuditLogFile() {
        return auditLogFile;
    }
}

