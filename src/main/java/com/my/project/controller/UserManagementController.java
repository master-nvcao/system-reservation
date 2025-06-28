package com.my.project.controller;

import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Optional;

public class UserManagementController {

    @FXML private TableView<Utilisateur> userTable;
    @FXML private TableColumn<Utilisateur, Long> idCol;
    @FXML private TableColumn<Utilisateur, String> nomCol;
    @FXML private TableColumn<Utilisateur, String> emailCol;
    @FXML private TableColumn<Utilisateur, String> roleCol;

    @FXML
    public void initialize() {
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getId()).asObject());
        nomCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getNom()));
        emailCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getEmail()));
        roleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRole()));

        chargerUtilisateurs();
    }

    private void chargerUtilisateurs() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Utilisateur> liste = session.createQuery("FROM Utilisateur", Utilisateur.class).list();
            userTable.setItems(FXCollections.observableArrayList(liste));
        }
    }

    @FXML
    private void handleAdd() {
        Dialog<Utilisateur> dialog = new Dialog<>();
        dialog.setTitle("Ajouter un utilisateur");
        dialog.setHeaderText("Entrez les informations du nouvel utilisateur");

        ButtonType addButtonType = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField nomField = new TextField();
        TextField emailField = new TextField();
        PasswordField mdpField = new PasswordField();
        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList("etudiant", "admin"));

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Email:"), 0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Mot de passe:"), 0, 2); grid.add(mdpField, 1, 2);
        grid.add(new Label("Rôle:"), 0, 3); grid.add(roleBox, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Utilisateur u = new Utilisateur();
                u.setNom(nomField.getText());
                u.setEmail(emailField.getText());
                u.setMotDePasse(mdpField.getText());
                u.setRole(roleBox.getValue());
                return u;
            }
            return null;
        });

        Optional<Utilisateur> result = dialog.showAndWait();
        result.ifPresent(utilisateur -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.persist(utilisateur);
                tx.commit();
                chargerUtilisateurs();
            }
        });
    }

    @FXML
    private void handleEdit() {
        Utilisateur selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Sélection requise", "Veuillez sélectionner un utilisateur à modifier.");
            return;
        }

        Dialog<Utilisateur> dialog = new Dialog<>();
        dialog.setTitle("Modifier utilisateur");
        dialog.setHeaderText("Modifier les informations de l'utilisateur");

        ButtonType updateButtonType = new ButtonType("Mettre à jour", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField nomField = new TextField(selected.getNom());
        TextField emailField = new TextField(selected.getEmail());
        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList("etudiant", "enseignant", "admin"));
        roleBox.setValue(selected.getRole());

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Email:"), 0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Rôle:"), 0, 2); grid.add(roleBox, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == updateButtonType) {
                selected.setNom(nomField.getText());
                selected.setEmail(emailField.getText());
                selected.setRole(roleBox.getValue());
                return selected;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(utilisateur -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.merge(utilisateur);
                tx.commit();
                chargerUtilisateurs();
            }
        });
    }

    @FXML
    private void handleDelete() {
        Utilisateur selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Sélection requise", "Veuillez sélectionner un utilisateur à supprimer.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(session.merge(selected));
            tx.commit();
            chargerUtilisateurs();
        }
    }

    @FXML
    private void handleResetPassword() {
        Utilisateur selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Sélection requise", "Veuillez sélectionner un utilisateur.");
            return;
        }

        selected.setMotDePasse("1234");
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(selected);
            tx.commit();
            showAlert("Réinitialisé", "Mot de passe réinitialisé à : 1234");
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) userTable.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
