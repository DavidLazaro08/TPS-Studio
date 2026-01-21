package com.tpsstudio.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class LoginViewModel {

    private final StringProperty user;
    private final StringProperty pass;
    private final BooleanProperty loginOk;

    public LoginViewModel() {
        this.user = new SimpleStringProperty("");
        this.pass = new SimpleStringProperty("");
        this.loginOk = new SimpleBooleanProperty(false);
    }

    // Getters para las propiedades
    public StringProperty userProperty() {
        return user;
    }

    public String getUser() {
        return user.get();
    }

    public void setUser(String user) {
        this.user.set(user);
    }

    public StringProperty passProperty() {
        return pass;
    }

    public String getPass() {
        return pass.get();
    }

    public void setPass(String pass) {
        this.pass.set(pass);
    }

    public BooleanProperty loginOkProperty() {
        return loginOk;
    }

    public boolean isLoginOk() {
        return loginOk.get();
    }

    // Método de validación
    public boolean validateLogin() {
        boolean isValid = "Admin".equals(user.get()) && "Admin".equals(pass.get());
        loginOk.set(isValid);
        return isValid;
    }
}
