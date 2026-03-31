package am.froshy.mialu.launcher.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;

/**
 * Gestor de encriptación de tokens y datos sensibles.
 */
public final class TokenEncryption {
    
    private static final String CIPHER_ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    
    private final SecretKey secretKey;
    
    /**
     * Genera una nueva clave de encriptación.
     */
    public TokenEncryption() {
        this.secretKey = generateKey();
    }
    
    /**
     * Encripta un token.
     */
    public String encrypt(String token) throws Exception {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token no puede ser null o vacío");
        }
        
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] encryptedBytes = cipher.doFinal(token.getBytes());
        byte[] iv = cipher.getIV();
        
        // Combina IV + encrypted bytes
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * Desencripta un token.
     */
    public String decrypt(String encryptedToken) throws Exception {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw new IllegalArgumentException("Token encriptado no puede ser null o vacío");
        }
        
        byte[] combined = Base64.getDecoder().decode(encryptedToken);
        
        // Extrae IV y datos encriptados
        byte[] iv = Arrays.copyOfRange(combined, 0, 16);
        byte[] encryptedBytes = Arrays.copyOfRange(combined, 16, combined.length);
        
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }
    
    /**
     * Genera una clave segura para encriptación.
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(CIPHER_ALGORITHM);
            keyGen.init(KEY_SIZE, new SecureRandom());
            return keyGen.generateKey();
        } catch (Exception ex) {
            throw new RuntimeException("Error generando clave de encriptación", ex);
        }
    }
    
    /**
     * Limpia datos sensibles de la memoria.
     */
    public static void clearSensitiveData(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
    
    /**
     * Limpia datos sensibles de la memoria.
     */
    public static void clearSensitiveData(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }
}

