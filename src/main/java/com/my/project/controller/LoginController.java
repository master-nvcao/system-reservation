package com.my.project.controller;

import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.hibernate.Session;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;

    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Veuillez remplir tous les champs.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Utilisateur user = session.createQuery(
                            "FROM Utilisateur WHERE email = :email", Utilisateur.class)
                    .setParameter("email", email)
                    .uniqueResult();

            if (user != null && user.getMotDePasse().equals(password)) {
                messageLabel.setText("Connexion réussie !");
                messageLabel.setStyle("-fx-text-fill: green;");

                Stage stage = new Stage();
                Scene scene;

                if ("admin".equalsIgnoreCase(user.getRole())) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/admin_dashboard.fxml"));
                    scene = new Scene(loader.load());
                    AdminDashboardController controller = loader.getController();
                    controller.setUtilisateur(user);
                    stage.setTitle("Admin - Tableau de bord");
                } else {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dashboard.fxml"));
                    scene = new Scene(loader.load());
                    DashboardController controller = loader.getController();
                    controller.setUtilisateur(user);
                    stage.setTitle("Utilisateur - Tableau de bord");
                }

                stage.setScene(scene);
                stage.show();

                // Fermer la fenêtre de connexion
                ((Stage) emailField.getScene().getWindow()).close();
            } else {
                messageLabel.setText("Email ou mot de passe invalide.");
                messageLabel.setStyle("-fx-text-fill: red;");
                passwordField.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Erreur lors du chargement de l'interface.");
            messageLabel.setStyle("-fx-text-fill: red;");
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("Erreur de connexion à la base.");
            messageLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void handleShowRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/register.fxml"));
            Stage registerStage = new Stage();
            registerStage.setTitle("Admin Hub - Créer un compte");
            registerStage.setScene(new Scene(loader.load()));
            registerStage.setResizable(false);
            registerStage.centerOnScreen();
            registerStage.show();

            // Optionally close the login window
             ((Stage) emailField.getScene().getWindow()).close();

        } catch (IOException e) {
            e.printStackTrace();
            showMessage("Erreur lors de l'ouverture du formulaire d'inscription.", "error");
        }
    }

    private void showMessage(String message, String type) {
        messageLabel.setText(message);
        if ("success".equals(type)) {
            messageLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
        } else {
            messageLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        }
    }

    // Method to clear the form
    private void clearForm() {
        emailField.clear();
        passwordField.clear();
        messageLabel.setText("");
    }
}
