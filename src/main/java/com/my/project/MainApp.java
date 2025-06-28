package com.my.project;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("Connexion - Réservation de salles");
        primaryStage.setScene(scene);
        primaryStage.show();
    }


}
