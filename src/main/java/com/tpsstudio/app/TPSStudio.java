package com.tpsstudio.app;

import com.tpsstudio.util.ImageUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/* Punto de entrada de TPS Studio.
 * Carga la primera vista (login) y aplica la hoja de estilos general.
 *
 * Nota: al cerrar la app se limpia la caché de imágenes para liberar memoria. */

public class TPSStudio extends Application {

    // -----------------------------------------------------
    // Configuración básica de la ventana
    // -----------------------------------------------------
    private static final String APP_TITLE = "TPS Studio";
    private static final String LOGIN_FXML = "/fxml/login_view.fxml";
    private static final String APP_CSS = "/css/app.css";
    private static final int WINDOW_WIDTH = 760;
    private static final int WINDOW_HEIGHT = 580;

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(TPSStudio.class.getResource(LOGIN_FXML));

            Scene scene = new Scene(loader.load(), WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(TPSStudio.class.getResource(APP_CSS).toExternalForm());

            stage.setTitle(APP_TITLE);
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            // Si falla la carga del FXML/CSS, mejor dejar un mensaje claro
            System.err.println("No se pudo iniciar TPS Studio. Revisa los recursos FXML/CSS.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void stop() throws Exception {
        ImageUtils.limpiarCache();
        super.stop();
    }
}
