package am.froshy.mialu.launcher.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.time.Instant;

/**
 * Rate limiter para proteger contra ataques de fuerza bruta.
 */
public final class RateLimiter {
    private final int maxAttempts;
    private final long lockoutDurationMs;
    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    
    public RateLimiter(int maxAttempts, long lockoutDurationMs) {
        this.maxAttempts = maxAttempts;
        this.lockoutDurationMs = lockoutDurationMs;
    }
    
    /**
     * Verifica si se puede intentar una acción.
     */
    public boolean canAttempt(String identifier) {
        AttemptRecord record = attempts.get(identifier);
        
        if (record == null) {
            return true;
        }
        
        // Si el lockout ha expirado, permitir
        if (Instant.now().isAfter(record.lockoutUntil)) {
            attempts.remove(identifier);
            return true;
        }
        
        // Si aún hay intentos disponibles, permitir
        return record.attempts < maxAttempts;
    }
    
    /**
     * Registra un intento fallido.
     */
    public void recordAttempt(String identifier) {
        attempts.compute(identifier, (key, record) -> {
            if (record == null) {
                return new AttemptRecord(1);
            }
            
            int newAttempts = record.attempts + 1;
            
            // Si se alcanza el máximo, activar lockout
            if (newAttempts >= maxAttempts) {
                Instant lockoutUntil = Instant.now().plusMillis(lockoutDurationMs);
                return new AttemptRecord(newAttempts, lockoutUntil);
            }
            
            return new AttemptRecord(newAttempts);
        });
    }
    
    /**
     * Registra un intento exitoso (limpia el registro).
     */
    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }
    
    private static class AttemptRecord {
        final int attempts;
        final Instant lockoutUntil;
        
        AttemptRecord(int attempts) {
            this.attempts = attempts;
            this.lockoutUntil = null;
        }
        
        AttemptRecord(int attempts, Instant lockoutUntil) {
            this.attempts = attempts;
            this.lockoutUntil = lockoutUntil;
        }
    }
}

