package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Salle;
import com.my.project.model.Utilisateur;
import com.my.project.util.ExportUtil;
import com.my.project.util.HibernateUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AllReservationsController {

    @FXML private TextField salleFilter;
    @FXML private TextField userFilter;
    @FXML private DatePicker dateFilter;

    @FXML private TableView<Reservation> reservationTable;
    @FXML private TableColumn<Reservation, String> salleCol;
    @FXML private TableColumn<Reservation, String> userCol;
    @FXML private TableColumn<Reservation, String> dateDebutCol;
    @FXML private TableColumn<Reservation, String> dateFinCol;
    @FXML private TableColumn<Reservation, String> descCol;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        salleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSalle().getNom()));
        userCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUtilisateur().getEmail()));
        dateDebutCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDateDebut().format(formatter)));
        dateFinCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDateFin().format(formatter)));
        descCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));

        chargerReservations();
    }

    private void chargerReservations() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Reservation> list = session.createQuery("FROM Reservation", Reservation.class).list();
            reservationTable.setItems(FXCollections.observableArrayList(list));
        }
    }

    @FXML
    private void handleFiltrer() {
        String salleNom = salleFilter.getText().trim().toLowerCase();
        String utilisateurEmail = userFilter.getText().trim().toLowerCase();
        LocalDate date = dateFilter.getValue();

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Reservation WHERE 1=1";
            if (!salleNom.isEmpty()) hql += " AND LOWER(salle.nom) LIKE :salle";
            if (!utilisateurEmail.isEmpty()) hql += " AND LOWER(utilisateur.email) LIKE :email";
            if (date != null) hql += " AND dateDebut >= :start AND dateDebut < :end";

            var query = session.createQuery(hql, Reservation.class);
            if (!salleNom.isEmpty()) query.setParameter("salle", "%" + salleNom + "%");
            if (!utilisateurEmail.isEmpty()) query.setParameter("email", "%" + utilisateurEmail + "%");
            if (date != null) {
                query.setParameter("start", date.atStartOfDay());
                query.setParameter("end", date.plusDays(1).atStartOfDay());
            }

            reservationTable.setItems(FXCollections.observableArrayList(query.list()));
        }
    }

    @FXML
    private void handleSupprimer() {
        Reservation selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert("Sélection requise", "Veuillez sélectionner une réservation à supprimer.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(session.merge(selected));
            tx.commit();
            chargerReservations();
        }
    }

    @FXML
    private void handleModifier() {
        Reservation selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert("Sélection requise", "Veuillez sélectionner une réservation à modifier.");
            return;
        }

        // Création du dialogue personnalisé
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier la réservation");

        // Champs de formulaire
        DatePicker datePicker = new DatePicker(selected.getDateDebut().toLocalDate());
        TextField debutField = new TextField(selected.getDateDebut().toLocalTime().toString());
        TextField finField = new TextField(selected.getDateFin().toLocalTime().toString());
        ComboBox<Salle> salleCombo = new ComboBox<>();

        // Charger les salles disponibles
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Salle> salles = session.createQuery("FROM Salle WHERE disponible = true", Salle.class).list();
            salleCombo.getItems().addAll(salles);
        }
        salleCombo.setValue(selected.getSalle());

        // Organiser les éléments
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Date :"), datePicker);
        grid.addRow(1, new Label("Heure début :"), debutField);
        grid.addRow(2, new Label("Heure fin :"), finField);
        grid.addRow(3, new Label("Salle :"), salleCombo);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    LocalDate date = datePicker.getValue();
                    LocalTime debut = LocalTime.parse(debutField.getText());
                    LocalTime fin = LocalTime.parse(finField.getText());
                    Salle nouvelleSalle = salleCombo.getValue();

                    LocalDateTime dateDebut = LocalDateTime.of(date, debut);
                    LocalDateTime dateFin = LocalDateTime.of(date, fin);

                    // Vérification conflits salle
                    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                        List<Reservation> conflits = session.createQuery(
                                        "FROM Reservation WHERE salle = :salle AND id <> :id AND " +
                                                "((:debut BETWEEN dateDebut AND dateFin) OR " +
                                                "(:fin BETWEEN dateDebut AND dateFin) OR " +
                                                "(:debut < dateDebut AND :fin > dateFin))", Reservation.class)
                                .setParameter("salle", nouvelleSalle)
                                .setParameter("id", selected.getId())
                                .setParameter("debut", dateDebut)
                                .setParameter("fin", dateFin)
                                .list();

                        if (!conflits.isEmpty()) {
                            alert("Conflit", "Ce créneau est déjà réservé pour cette salle.");
                            return;
                        }

                        Transaction tx = session.beginTransaction();
                        Reservation modifiee = session.merge(selected);
                        modifiee.setDateDebut(dateDebut);
                        modifiee.setDateFin(dateFin);
                        modifiee.setSalle(nouvelleSalle);
                        session.update(modifiee);
                        tx.commit();
                        chargerReservations();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    alert("Erreur", "La modification a échoué.");
                }
            }
        });
    }


    @FXML
    private void handleExporter() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter les réservations");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichier PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Fichier Excel", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(reservationTable.getScene().getWindow());
        if (file != null) {
            if (file.getName().endsWith(".pdf")) {
                ExportUtil.exportPDF(reservationTable.getItems(), file);
            } else if (file.getName().endsWith(".xlsx")) {
                ExportUtil.exportExcel(reservationTable.getItems(), file);
            } else {
                alert("Format non pris en charge", "Choisissez .pdf ou .xlsx uniquement.");
            }
        }
    }

    @FXML
    private void handleFermer() {
        Stage stage = (Stage) reservationTable.getScene().getWindow();
        stage.close();
    }

    private void alert(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }




}
