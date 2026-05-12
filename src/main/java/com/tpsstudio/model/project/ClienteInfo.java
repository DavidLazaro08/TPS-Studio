package com.tpsstudio.model.project;

import java.util.regex.Pattern;

/**
 * Datos de contacto del cliente asociado a un proyecto.
 *
 * Se guardan junto a los metadatos del proyecto y también pueden exportarse
 * como archivo de texto dentro de la carpeta del trabajo.
 */
public class ClienteInfo {

    private String nombreEmpresa;
    private String nombreContacto;
    private String email;
    private String telefono;
    private String observaciones;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern TELEFONO_PATTERN = Pattern.compile(
            "^[+]?[0-9\\s\\-()]{9,20}$");

    // =====================================================
    // Constructor
    // =====================================================

    public ClienteInfo() {
        this.nombreEmpresa = "";
        this.nombreContacto = "";
        this.email = "";
        this.telefono = "";
        this.observaciones = "";
    }

    // =====================================================
    // Datos del cliente
    // =====================================================

    public String getNombreEmpresa() {
        return nombreEmpresa;
    }

    public void setNombreEmpresa(String nombreEmpresa) {
        this.nombreEmpresa = nombreEmpresa != null ? nombreEmpresa : "";
    }

    public String getNombreContacto() {
        return nombreContacto;
    }

    public void setNombreContacto(String nombreContacto) {
        this.nombreContacto = nombreContacto != null ? nombreContacto : "";
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email != null ? email : "";
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono != null ? telefono : "";
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones != null ? observaciones : "";
    }

    // =====================================================
    // Validaciones
    // =====================================================

    public static boolean validarEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return true;
        }

        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean validarTelefono(String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) {
            return true;
        }

        return TELEFONO_PATTERN.matcher(telefono.trim()).matches();
    }

    public boolean tieneInformacion() {
        return !nombreEmpresa.trim().isEmpty()
                || !nombreContacto.trim().isEmpty()
                || !email.trim().isEmpty()
                || !telefono.trim().isEmpty()
                || !observaciones.trim().isEmpty();
    }

    // =====================================================
    // Representación
    // =====================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (!nombreEmpresa.isEmpty()) {
            sb.append("Empresa: ").append(nombreEmpresa).append("\n");
        }

        if (!nombreContacto.isEmpty()) {
            sb.append("Contacto: ").append(nombreContacto).append("\n");
        }

        if (!email.isEmpty()) {
            sb.append("Email: ").append(email).append("\n");
        }

        if (!telefono.isEmpty()) {
            sb.append("Teléfono: ").append(telefono).append("\n");
        }

        if (!observaciones.isEmpty()) {
            sb.append("Observaciones: ").append(observaciones);
        }

        return sb.toString();
    }
}