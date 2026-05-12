package com.tpsstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tpsstudio.model.auth.License;
import com.tpsstudio.model.auth.LocalUser;
import com.tpsstudio.util.LocalDateTimeAdapter;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de autenticación y activación local.
 *
 * Gestiona la licencia activa, los usuarios locales y la sesión actual
 * mediante un archivo JSON guardado en el equipo del usuario.
 */
public class AuthService {

    private static AuthService instance;

    private static final String APP_DIR = ".tpsstudio";
    private static final String AUTH_FILE = "auth.json";

    private final Gson gson;
    private final Path authPath;

    private AuthData data;
    private String currentUser;

    private static class AuthData {
        private License license;
        private List<LocalUser> users = new ArrayList<>();
    }

    private AuthService() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        String userHome = System.getProperty("user.home");
        this.authPath = Paths.get(userHome, APP_DIR, AUTH_FILE);

        load();
    }

    public static synchronized AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    // =====================================================
    // Persistencia local
    // =====================================================

    private void load() {
        if (Files.exists(authPath)) {
            try (FileReader reader = new FileReader(authPath.toFile())) {
                this.data = gson.fromJson(reader, AuthData.class);
            } catch (IOException e) {
                e.printStackTrace();
                this.data = new AuthData();
            }
        }

        if (this.data == null) {
            this.data = new AuthData();
        }
    }

    private void save() {
        try {
            Files.createDirectories(authPath.getParent());

            try (FileWriter writer = new FileWriter(authPath.toFile())) {
                gson.toJson(this.data, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =====================================================
    // Licencia
    // =====================================================

    public boolean isActivated() {
        return data.license != null && data.license.isActive();
    }

    public boolean activate(String licenseKey, String username, String email, String password) {
        if (licenseKey == null) return false;

        String licenseType = null;

        try (java.io.InputStream is = getClass().getResourceAsStream("/auth/licencias.txt");
             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {

            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;

                String[] parts = line.split("\\|");

                if (parts.length >= 2 && parts[0].trim().equals(licenseKey.trim())) {
                    licenseType = parts[1].trim();
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (licenseType == null) {
            return false;
        }

        License newLicense = new License(licenseKey, licenseType);
        LocalUser admin = new LocalUser(username, email, password);

        this.data.license = newLicense;

        if (this.data.users == null) {
            this.data.users = new ArrayList<>();
        }

        this.data.users.add(admin);
        this.currentUser = username;

        save();
        return true;
    }

    public License getLicense() {
        return data.license;
    }

    // =====================================================
    // Sesión
    // =====================================================

    public boolean login(String username, String password) {
        // Acceso maestro para pruebas y recuperación.
        if ("Admin".equalsIgnoreCase(username) && "Admin".equals(password)) {
            this.currentUser = "Admin";
            return true;
        }

        if (data.users == null) return false;

        boolean success = data.users.stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username) && u.getPassword().equals(password));

        if (success) {
            this.currentUser = username;
        }

        return success;
    }

    public String getCurrentUser() {
        return currentUser != null ? currentUser : "Invitado";
    }

    public void logout() {
        this.currentUser = null;
    }
}