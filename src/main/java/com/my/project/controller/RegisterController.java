package com.my.project.controller;

import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.IOException;

public class RegisterController {

    @FXML private TextField nomField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        roleComboBox.getItems().addAll("etudiant", "admin");
        roleComboBox.setPromptText("Choisir un rôle");
        messageLabel.setText(""); // Clear message at init
    }

    @FXML
    private void handleRegister() {
        String nom = nomField.getText().trim();
        String email = emailField.getText().trim();
        String mdp = passwordField.getText();
        String role = roleComboBox.getValue();

        if (nom.isEmpty() || email.isEmpty() || mdp.isEmpty() || role == null) {
            messageLabel.setTextFill(Color.RED);
            messageLabel.setText("Tous les champs sont obligatoires.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean emailExists = session.createQuery(
                            "FROM Utilisateur WHERE email = :email", Utilisateur.class)
                    .setParameter("email", email)
                    .uniqueResult() != null;

            if (emailExists) {
                messageLabel.setTextFill(Color.RED);
                messageLabel.setText("Cet email est déjà utilisé.");
                return;
            }

            Utilisateur u = new Utilisateur();
            u.setNom(nom);
            u.setEmail(email);
            u.setMotDePasse(mdp); // À améliorer avec hashing
            u.setRole(role);

            Transaction tx = session.beginTransaction();
            session.persist(u);
            tx.commit();

            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Compte créé avec succès !");
            clearForm();
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setTextFill(Color.RED);
            messageLabel.setText("Erreur lors de l'inscription.");
        }
    }


    @FXML
    private void handleShowLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            Stage loginStage = new Stage();
            loginStage.setTitle("The Hub - Connexion");
            loginStage.setScene(new Scene(loader.load()));
            loginStage.setResizable(false);
            loginStage.centerOnScreen();
            loginStage.show();

            // Close the register window
            ((Stage) emailField.getScene().getWindow()).close();

        } catch (IOException e) {
            e.printStackTrace();
            showMessage("Erreur lors de l'ouverture du formulaire de connexion.", "error");
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

    private void clearForm() {
        nomField.clear();
        emailField.clear();
        passwordField.clear();
        roleComboBox.getSelectionModel().clearSelection();
    }
}
