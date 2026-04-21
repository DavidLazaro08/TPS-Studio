package com.tpsstudio.model.auth;

import java.time.LocalDateTime;

/**
 * Representa un usuario local registrado tras la activación.
 */
public class LocalUser {
    private String username;
    private String email;
    private String password; // En un sistema real deberia ir hasheada
    private LocalDateTime registrationDate;

    public LocalUser() {}

    public LocalUser(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.registrationDate = LocalDateTime.now();
    }

    // Getters y Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }
}
