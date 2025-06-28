package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class MyReservationsController {

    @FXML private TableView<Reservation> reservationTable;
    @FXML private TableColumn<Reservation, String> salleCol;
    @FXML private TableColumn<Reservation, String> dateDebutCol;
    @FXML private TableColumn<Reservation, String> dateFinCol;
    @FXML private TableColumn<Reservation, String> descCol;

    private Utilisateur utilisateur;

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
        chargerReservations();
    }

    @FXML
    public void initialize() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        salleCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getSalle().getNom()));
        dateDebutCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getDateDebut().format(dtf)));
        dateFinCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getDateFin().format(dtf)));
        descCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));
    }

    private void chargerReservations() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Reservation> reservations = session.createQuery(
                            "FROM Reservation WHERE utilisateur = :user ORDER BY dateDebut DESC", Reservation.class)
                    .setParameter("user", utilisateur)
                    .list();

            reservationTable.setItems(FXCollections.observableArrayList(reservations));
        }
    }

    @FXML
    private void handleModifier() {
        Reservation selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Sélection requise", "Veuillez sélectionner une réservation à modifier.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.getDescription());
        dialog.setTitle("Modifier la description");
        dialog.setHeaderText("Nouvelle description :");
        dialog.showAndWait().ifPresent(desc -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                selected.setDescription(desc);
                session.merge(selected);
                tx.commit();
                chargerReservations();
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Erreur", "Échec de la modification.");
            }
        });
    }

    @FXML
    private void handleAnnuler() {
        Reservation selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Sélection requise", "Veuillez sélectionner une réservation à annuler.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(session.merge(selected));
            tx.commit();
            chargerReservations();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Erreur", "Impossible d’annuler la réservation.");
        }
    }

    @FXML
    private void handleFermer() {
        Stage stage = (Stage) reservationTable.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String titre, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
