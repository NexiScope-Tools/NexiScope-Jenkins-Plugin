package io.nexiscope.jenkins.plugin.security;

import okhttp3.CertificatePinner;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Certificate pinning configuration for enhanced security.
 * 
 * Supports pinning certificates by hostname and SHA-256 hash.
 * This prevents man-in-the-middle attacks by ensuring only specific
 * certificates are accepted for connections.
 * 
 * @author NexiScope Team
 */
public class CertificatePinningConfig {
    
    private static final Logger LOGGER = Logger.getLogger(CertificatePinningConfig.class.getName());
    
    // Pattern to match SHA-256 hash (64 hex characters)
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    
    private final List<PinEntry> pins = new ArrayList<>();
    private boolean enabled = false;
    
    /**
     * Creates a CertificatePinner from configuration string.
     * 
     * Format: "hostname1:hash1,hash2;hostname2:hash3"
     * Or: "hostname:hash" for single pin
     * 
     * @param pinConfig Configuration string, or null/empty to disable
     * @return CertificatePinner instance
     */
    public static CertificatePinningConfig fromConfig(String pinConfig) {
        CertificatePinningConfig pinner = new CertificatePinningConfig();
        
        if (pinConfig == null || pinConfig.trim().isEmpty()) {
            return pinner; // Disabled
        }
        
        pinner.enabled = true;
        String[] entries = pinConfig.split(";");
        
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                LOGGER.warning("Invalid certificate pin entry format: " + entry + ". Expected 'hostname:hash1,hash2'");
                continue;
            }
            
            String hostname = parts[0].trim();
            String[] hashes = parts[1].split(",");
            
            for (String hash : hashes) {
                hash = hash.trim();
                if (hash.isEmpty()) {
                    continue;
                }
                
                // Validate SHA-256 hash format
                if (!SHA256_PATTERN.matcher(hash).matches()) {
                    LOGGER.warning("Invalid SHA-256 hash format: " + hash + ". Expected 64 hex characters.");
                    continue;
                }
                
                pinner.pins.add(new PinEntry(hostname, hash));
                LOGGER.fine("Added certificate pin: " + hostname + " -> " + hash);
            }
        }
        
        if (pinner.pins.isEmpty()) {
            LOGGER.warning("No valid certificate pins found in configuration. Certificate pinning disabled.");
            pinner.enabled = false;
        } else {
            LOGGER.info("Certificate pinning enabled with " + pinner.pins.size() + " pin(s)");
        }
        
        return pinner;
    }
    
    /**
     * Creates an OkHttp CertificatePinner from this configuration.
     * 
     * @return OkHttp CertificatePinner, or null if pinning is disabled
     */
    public CertificatePinner toOkHttpPinner() {
        if (!enabled || pins.isEmpty()) {
            return null;
        }
        
        CertificatePinner.Builder builder = new CertificatePinner.Builder();
        
        // Group pins by hostname
        java.util.Map<String, List<String>> hostPins = new java.util.HashMap<>();
        for (PinEntry pin : pins) {
            hostPins.computeIfAbsent(pin.hostname, k -> new ArrayList<>()).add("sha256/" + pin.hash);
        }
        
        // Add pins to OkHttp builder
        for (java.util.Map.Entry<String, List<String>> entry : hostPins.entrySet()) {
            String hostname = entry.getKey();
            List<String> hashes = entry.getValue();
            builder.add(hostname, hashes.toArray(new String[0]));
            LOGGER.fine("Added OkHttp certificate pin for " + hostname + " with " + hashes.size() + " hash(es)");
        }
        
        return builder.build();
    }
    
    /**
     * Checks if certificate pinning is enabled.
     */
    public boolean isEnabled() {
        return enabled && !pins.isEmpty();
    }
    
    /**
     * Gets the number of configured pins.
     */
    public int getPinCount() {
        return pins.size();
    }
    
    /**
     * Internal class to store pin entries.
     */
    private static class PinEntry {
        final String hostname;
        final String hash;
        
        PinEntry(String hostname, String hash) {
            this.hostname = hostname;
            this.hash = hash;
        }
    }
}

