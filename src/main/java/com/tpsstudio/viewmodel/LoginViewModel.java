package com.tpsstudio.viewmodel;

import com.tpsstudio.service.AuthService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel de la pantalla de login.
 *
 * Mantiene usuario, contraseña y resultado de validación.
 */
public class LoginViewModel {

    private final StringProperty user;
    private final StringProperty pass;
    private final BooleanProperty loginOk;

    public LoginViewModel() {
        this.user = new SimpleStringProperty("");
        this.pass = new SimpleStringProperty("");
        this.loginOk = new SimpleBooleanProperty(false);
    }

    // =====================================================
    // Usuario
    // =====================================================

    public StringProperty userProperty() {
        return user;
    }

    public String getUser() {
        return user.get();
    }

    public void setUser(String user) {
        this.user.set(user);
    }

    // =====================================================
    // Contraseña
    // =====================================================

    public StringProperty passProperty() {
        return pass;
    }

    public String getPass() {
        return pass.get();
    }

    public void setPass(String pass) {
        this.pass.set(pass);
    }

    // =====================================================
    // Estado de login
    // =====================================================

    public BooleanProperty loginOkProperty() {
        return loginOk;
    }

    public boolean isLoginOk() {
        return loginOk.get();
    }

    public boolean validateLogin() {
        boolean isValid = AuthService.getInstance().login(user.get(), pass.get());
        loginOk.set(isValid);
        return isValid;
    }
}