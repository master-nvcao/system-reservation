package com.my.project.controller;

import com.my.project.model.Utilisateur;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

public class AdminDashboardController {

    @FXML private Label adminWelcomeLabel;
    private Utilisateur utilisateur;

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
        adminWelcomeLabel.setText("Admin : " + utilisateur.getNom());
    }

    @FXML
    private void handleUserManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_management.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Gestion des utilisateurs");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void handleRoomManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/room_management.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Gestion des salles");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void handleEquipementManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/equipement_management.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Gestion des équipements");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void handleAllReservations() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/all_reservations.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Toutes les réservations");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void handleLogout() {
        Stage stage = (Stage) adminWelcomeLabel.getScene().getWindow();
        stage.close();
    }
}
