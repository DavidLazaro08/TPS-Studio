package com.tpsstudio.model.auth;

import java.time.LocalDateTime;

/**
 * Representa una licencia local de activación de TPS Studio.
 *
 * Guarda la clave usada, el tipo de licencia, la fecha de activación
 * y si actualmente está activa.
 */
public class License {

    private String key;
    private LocalDateTime activationDate;
    private boolean active;
    private String type;

    // =====================================================
    // Constructores
    // =====================================================

    public License() {
    }

    public License(String key, String type) {
        this.key = key;
        this.type = type;
        this.activationDate = LocalDateTime.now();
        this.active = true;
    }

    // =====================================================
    // Getters y setters
    // =====================================================

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public LocalDateTime getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(LocalDateTime activationDate) {
        this.activationDate = activationDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}