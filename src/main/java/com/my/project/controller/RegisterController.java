package com.my.project.controller;

import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.hibernate.Session;
import org.hibernate.Transaction;

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

    private void clearForm() {
        nomField.clear();
        emailField.clear();
        passwordField.clear();
        roleComboBox.getSelectionModel().clearSelection();
    }
}
