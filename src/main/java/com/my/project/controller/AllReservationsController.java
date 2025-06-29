package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Salle;
import com.my.project.model.Utilisateur;
import com.my.project.service.ReservationService;
import com.my.project.service.ReportService;
import com.my.project.util.HibernateUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class AllReservationsController implements Initializable {

    // Filters
    @FXML private TextField salleFilter;
    @FXML private TextField userFilter;
    @FXML private DatePicker dateFilter;
    @FXML private ComboBox<String> statusFilter;

    // Statistics
    @FXML private Label totalReservationsLabel;
    @FXML private Label todayReservationsLabel;
    @FXML private Label activeReservationsLabel;
    @FXML private Label upcomingReservationsLabel;

    // Table
    @FXML private TableView<ReservationDisplay> reservationTable;
    @FXML private TableColumn<ReservationDisplay, Long> idCol;
    @FXML private TableColumn<ReservationDisplay, String> salleCol;
    @FXML private TableColumn<ReservationDisplay, String> userCol;
    @FXML private TableColumn<ReservationDisplay, String> dateDebutCol;
    @FXML private TableColumn<ReservationDisplay, String> dateFinCol;
    @FXML private TableColumn<ReservationDisplay, String> durationCol;
    @FXML private TableColumn<ReservationDisplay, String> statusCol;
    @FXML private TableColumn<ReservationDisplay, String> descCol;
    @FXML private TableColumn<ReservationDisplay, Void> actionsCol;

    // Info labels
    @FXML private Label tableInfoLabel;
    @FXML private Label selectionInfoLabel;

    private final ReservationService reservationService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private ObservableList<ReservationDisplay> allReservations;
    private Utilisateur currentUser;

    public AllReservationsController() {
        this.reservationService = new ReservationService();
        this.allReservations = FXCollections.observableArrayList();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupStatusFilter();
        setupTableColumns();
        setupTableSelection();
        loadReservations();
        loadStatistics();
    }

    public void setCurrentUser(Utilisateur user) {
        this.currentUser = user;
    }

    private void setupStatusFilter() {
        ObservableList<String> statusOptions = FXCollections.observableArrayList(
                "Tous les statuts", "En attente", "Approuvées", "Rejetées", "En cours", "À venir", "Terminées"
        );
        statusFilter.setItems(statusOptions);
        statusFilter.setValue("Tous les statuts");
    }

    private void setupTableColumns() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        salleCol.setCellValueFactory(new PropertyValueFactory<>("salleName"));
        userCol.setCellValueFactory(new PropertyValueFactory<>("userName"));
        dateDebutCol.setCellValueFactory(new PropertyValueFactory<>("dateDebut"));
        dateFinCol.setCellValueFactory(new PropertyValueFactory<>("dateFin"));
        durationCol.setCellValueFactory(new PropertyValueFactory<>("duration"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Setup actions column with approval buttons
        setupActionsColumn();

        // Setup row styling based on status
        reservationTable.setRowFactory(tv -> {
            TableRow<ReservationDisplay> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.getStyleClass().removeAll("row-pending", "row-approved", "row-rejected", "row-active", "row-upcoming", "row-finished");
                } else {
                    row.getStyleClass().removeAll("row-pending", "row-approved", "row-rejected", "row-active", "row-upcoming", "row-finished");
                    switch (newItem.getStatus()) {
                        case "En attente":
                            row.getStyleClass().add("row-pending");
                            break;
                        case "En cours":
                            row.getStyleClass().add("row-active");
                            break;
                        case "À venir":
                            row.getStyleClass().add("row-upcoming");
                            break;
                        case "Terminée":
                            row.getStyleClass().add("row-finished");
                            break;
                        case "Rejetée":
                            row.getStyleClass().add("row-rejected");
                            break;
                    }
                }
            });
            return row;
        });
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(param -> new TableCell<ReservationDisplay, Void>() {
            private final Button approveBtn = new Button("✅");
            private final Button rejectBtn = new Button("❌");
            private final Button editBtn = new Button("✏️");
            private final Button deleteBtn = new Button("🗑️");

            {
                approveBtn.getStyleClass().addAll("table-action-button", "approve-button");
                rejectBtn.getStyleClass().addAll("table-action-button", "reject-button");
                editBtn.getStyleClass().add("table-action-button");
                deleteBtn.getStyleClass().add("table-action-button");

                approveBtn.setTooltip(new Tooltip("Approuver"));
                rejectBtn.setTooltip(new Tooltip("Rejeter"));
                editBtn.setTooltip(new Tooltip("Modifier"));
                deleteBtn.setTooltip(new Tooltip("Supprimer"));

                approveBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleApprouverReservation(item);
                });

                rejectBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleRejeterReservation(item);
                });

                editBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleModifierReservation(item);
                });

                deleteBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleSupprimerReservation(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReservationDisplay reservation = getTableView().getItems().get(getIndex());
                    javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(3);

                    if ("En attente".equals(reservation.getStatus())) {
                        // Show approval buttons for pending requests
                        buttons.getChildren().addAll(approveBtn, rejectBtn);
                    } else {
                        // Show edit/delete for approved/other statuses
                        buttons.getChildren().addAll(editBtn, deleteBtn);
                    }
                    setGraphic(buttons);
                }
            }
        });
    }

    private void handleApprouverReservation(ReservationDisplay reservationDisplay) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
            if (reservation == null) {
                showErrorDialog("Erreur", "Réservation introuvable.");
                return;
            }

            // Check for conflicts with other approved reservations
            List<Reservation> conflits = session.createQuery(
                            "FROM Reservation WHERE salle = :salle AND id <> :id AND statut = :statut AND " +
                                    "((:debut < dateFin AND :fin > dateDebut))", Reservation.class)
                    .setParameter("salle", reservation.getSalle())
                    .setParameter("id", reservation.getId())
                    .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                    .setParameter("debut", reservation.getDateDebut())
                    .setParameter("fin", reservation.getDateFin())
                    .list();

            if (!conflits.isEmpty()) {
                showWarningDialog("Conflit détecté",
                        "Il y a déjà une réservation approuvée pour cette salle à ce créneau.\n" +
                                "Veuillez vérifier les conflits avant d'approuver.");
                return;
            }

            // Show approval dialog with optional comment
            TextInputDialog commentDialog = new TextInputDialog();
            commentDialog.setTitle("Approuver la réservation");
            commentDialog.setHeaderText("Approuver la réservation #" + reservation.getId());
            commentDialog.setContentText("Commentaire (optionnel):");

            Optional<String> result = commentDialog.showAndWait();
            if (result.isPresent()) {
                Transaction tx = session.beginTransaction();
                try {
                    Reservation merged = session.merge(reservation);
                    merged.setStatut(Reservation.StatutReservation.APPROUVEE);
                    merged.setDateValidation(LocalDateTime.now());
                    merged.setValidateurAdmin(currentUser);
                    merged.setCommentaireAdmin(result.get());

                    session.update(merged);
                    tx.commit();

                    loadReservations(); // Refresh table
                    loadStatistics(); // Refresh stats
                    showSuccessDialog("Succès", "Réservation approuvée avec succès.");

                } catch (Exception e) {
                    tx.rollback();
                    throw e;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de l'approbation: " + e.getMessage());
        }
    }

    private void handleRejeterReservation(ReservationDisplay reservationDisplay) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
            if (reservation == null) {
                showErrorDialog("Erreur", "Réservation introuvable.");
                return;
            }

            // Show rejection dialog with required reason
            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("Rejeter la réservation");
            reasonDialog.setHeaderText("Rejeter la réservation #" + reservation.getId());
            reasonDialog.setContentText("Motif du rejet (obligatoire):");

            Optional<String> result = reasonDialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                Transaction tx = session.beginTransaction();
                try {
                    Reservation merged = session.merge(reservation);
                    merged.setStatut(Reservation.StatutReservation.REJETEE);
                    merged.setDateValidation(LocalDateTime.now());
                    merged.setValidateurAdmin(currentUser);
                    merged.setCommentaireAdmin(result.get());

                    session.update(merged);
                    tx.commit();

                    loadReservations(); // Refresh table
                    loadStatistics(); // Refresh stats
                    showSuccessDialog("Succès", "Réservation rejetée.");

                } catch (Exception e) {
                    tx.rollback();
                    throw e;
                }
            } else if (result.isPresent()) {
                showWarningDialog("Motif requis", "Veuillez saisir un motif pour le rejet.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors du rejet: " + e.getMessage());
        }
    }

    private void setupTableSelection() {
        reservationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectionInfoLabel.setText(newSelection.getSalleName() + " - " + newSelection.getUserName());
            } else {
                selectionInfoLabel.setText("Aucun");
            }
        });
    }

    private void loadReservations() {
        Task<List<Reservation>> loadTask = new Task<List<Reservation>>() {
            @Override
            protected List<Reservation> call() throws Exception {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    return session.createQuery(
                            "FROM Reservation r LEFT JOIN FETCH r.salle LEFT JOIN FETCH r.utilisateur LEFT JOIN FETCH r.validateurAdmin ORDER BY r.dateCreation DESC",
                            Reservation.class).list();
                }
            }

            @Override
            protected void succeeded() {
                List<Reservation> reservations = getValue();
                Platform.runLater(() -> {
                    allReservations.clear();
                    for (Reservation r : reservations) {
                        allReservations.add(new ReservationDisplay(r));
                    }
                    reservationTable.setItems(allReservations);
                    updateTableInfo();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showErrorDialog("Erreur de chargement",
                            "Impossible de charger les réservations: " + getException().getMessage());
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void loadStatistics() {
        Task<Void> statsTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    long total = session.createQuery("SELECT COUNT(*) FROM Reservation", Long.class).uniqueResult();

                    LocalDateTime today = LocalDate.now().atStartOfDay();
                    LocalDateTime tomorrow = today.plusDays(1);
                    long todayCount = session.createQuery(
                                    "SELECT COUNT(*) FROM Reservation WHERE dateDebut >= :start AND dateDebut < :end AND statut = :statut", Long.class)
                            .setParameter("start", today)
                            .setParameter("end", tomorrow)
                            .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                            .uniqueResult();

                    long approved = session.createQuery("SELECT COUNT(*) FROM Reservation WHERE statut = :statut", Long.class)
                            .setParameter("statut", Reservation.StatutReservation.APPROUVEE).uniqueResult();

                    LocalDateTime now = LocalDateTime.now();
                    long upcoming = session.createQuery(
                                    "SELECT COUNT(*) FROM Reservation WHERE dateDebut > :now AND statut = :statut", Long.class)
                            .setParameter("now", now)
                            .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                            .uniqueResult();

                    Platform.runLater(() -> {
                        totalReservationsLabel.setText(String.valueOf(total));
                        todayReservationsLabel.setText(String.valueOf(todayCount));
                        activeReservationsLabel.setText(String.valueOf(approved));
                        upcomingReservationsLabel.setText(String.valueOf(upcoming));
                    });
                }
                return null;
            }
        };

        Thread statsThread = new Thread(statsTask);
        statsThread.setDaemon(true);
        statsThread.start();
    }

    @FXML
    private void handleFiltrer() {
        String salleNom = salleFilter.getText().trim().toLowerCase();
        String utilisateurEmail = userFilter.getText().trim().toLowerCase();
        LocalDate date = dateFilter.getValue();
        String status = statusFilter.getValue();

        ObservableList<ReservationDisplay> filteredList = FXCollections.observableArrayList();

        for (ReservationDisplay r : allReservations) {
            boolean matches = true;

            if (!salleNom.isEmpty() && !r.getSalleName().toLowerCase().contains(salleNom)) {
                matches = false;
            }

            if (!utilisateurEmail.isEmpty() && !r.getUserName().toLowerCase().contains(utilisateurEmail)) {
                matches = false;
            }

            if (date != null && !r.getDateDebutDateTime().toLocalDate().equals(date)) {
                matches = false;
            }

            if (status != null && !status.equals("Tous les statuts") && !r.getStatus().equals(status)) {
                matches = false;
            }

            if (matches) {
                filteredList.add(r);
            }
        }

        reservationTable.setItems(filteredList);
        updateTableInfo();
    }

    @FXML
    private void handleClearFilters() {
        salleFilter.clear();
        userFilter.clear();
        dateFilter.setValue(null);
        statusFilter.setValue("Tous les statuts");
        reservationTable.setItems(allReservations);
        updateTableInfo();
    }

    @FXML
    private void handleRefresh() {
        loadReservations();
        loadStatistics();
    }

    @FXML
    private void handleModifier() {
        ReservationDisplay selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner une réservation à modifier.");
            return;
        }
        handleModifierReservation(selected);
    }

    private void handleModifierReservation(ReservationDisplay reservationDisplay) {
        // Only allow modification of approved reservations that haven't started yet
        if (!"À venir".equals(reservationDisplay.getStatus())) {
            showWarningDialog("Modification impossible", "Seules les réservations à venir peuvent être modifiées.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
            if (reservation == null) {
                showErrorDialog("Erreur", "Réservation introuvable.");
                return;
            }

            // Create modification dialog
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Modifier la réservation");
            dialog.setHeaderText("Modification de la réservation #" + reservation.getId());

            // Form fields
            DatePicker datePicker = new DatePicker(reservation.getDateDebut().toLocalDate());
            TextField debutField = new TextField(reservation.getDateDebut().toLocalTime().toString());
            TextField finField = new TextField(reservation.getDateFin().toLocalTime().toString());
            TextArea descField = new TextArea(reservation.getDescription());
            descField.setPrefRowCount(3);

            ComboBox<Salle> salleCombo = new ComboBox<>();
            List<Salle> salles = session.createQuery("FROM Salle WHERE disponible = true", Salle.class).list();
            salleCombo.getItems().addAll(salles);
            salleCombo.setValue(reservation.getSalle());

            // Layout
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(15);
            grid.addRow(0, new Label("Date :"), datePicker);
            grid.addRow(1, new Label("Heure début :"), debutField);
            grid.addRow(2, new Label("Heure fin :"), finField);
            grid.addRow(3, new Label("Salle :"), salleCombo);
            grid.addRow(4, new Label("Description :"), descField);

            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                updateReservation(reservation, datePicker.getValue(),
                        LocalTime.parse(debutField.getText()),
                        LocalTime.parse(finField.getText()),
                        salleCombo.getValue(), descField.getText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de l'ouverture du dialogue de modification.");
        }
    }

    private void updateReservation(Reservation reservation, LocalDate date, LocalTime debut,
                                   LocalTime fin, Salle salle, String description) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            LocalDateTime dateDebut = LocalDateTime.of(date, debut);
            LocalDateTime dateFin = LocalDateTime.of(date, fin);

            // Check for conflicts
            List<Reservation> conflits = session.createQuery(
                            "FROM Reservation WHERE salle = :salle AND id <> :id AND statut = :statut AND " +
                                    "((:debut < dateFin AND :fin > dateDebut))", Reservation.class)
                    .setParameter("salle", salle)
                    .setParameter("id", reservation.getId())
                    .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                    .setParameter("debut", dateDebut)
                    .setParameter("fin", dateFin)
                    .list();

            if (!conflits.isEmpty()) {
                showWarningDialog("Conflit détecté", "Ce créneau est déjà réservé pour cette salle.");
                return;
            }

            Transaction tx = session.beginTransaction();
            Reservation merged = session.merge(reservation);
            merged.setDateDebut(dateDebut);
            merged.setDateFin(dateFin);
            merged.setSalle(salle);
            merged.setDescription(description);
            session.update(merged);
            tx.commit();

            loadReservations(); // Refresh table
            showSuccessDialog("Succès", "Réservation modifiée avec succès.");

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de la modification: " + e.getMessage());
        }
    }

    @FXML
    private void handleSupprimer() {
        ReservationDisplay selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner une réservation à supprimer.");
            return;
        }
        handleSupprimerReservation(selected);
    }

    private void handleSupprimerReservation(ReservationDisplay reservationDisplay) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation de suppression");
        confirmation.setHeaderText("Supprimer la réservation");
        confirmation.setContentText("Êtes-vous sûr de vouloir supprimer cette réservation ?\n\n" +
                "Salle: " + reservationDisplay.getSalleName() + "\n" +
                "Utilisateur: " + reservationDisplay.getUserName() + "\n" +
                "Date: " + reservationDisplay.getDateDebut() + "\n" +
                "Statut: " + reservationDisplay.getStatus());

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
                if (reservation != null) {
                    session.remove(reservation);
                    tx.commit();
                    loadReservations(); // Refresh table
                    loadStatistics(); // Refresh stats
                    showSuccessDialog("Succès", "Réservation supprimée avec succès.");
                } else {
                    showErrorDialog("Erreur", "Réservation introuvable.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog("Erreur", "Erreur lors de la suppression: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleDuplicate() {
        ReservationDisplay selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner une réservation à dupliquer.");
            return;
        }
        // Implementation for duplication would go here
        showInfoDialog("Fonctionnalité", "Duplication de réservation - À implémenter");
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
            exportReservations(file);
        }
    }

    private void exportReservations(File file) {
        Task<String> exportTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                ReportService reportService = new ReportService();
                if (file.getName().endsWith(".pdf")) {
                    return reportService.generatePDFReport("reservations");
                } else {
                    return reportService.generateExcelReport("reservations");
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    showSuccessDialog("Export réussi", "Fichier exporté: " + getValue());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showErrorDialog("Erreur d'export", "Erreur lors de l'export: " + getException().getMessage());
                });
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }

    @FXML
    private void handleFermer() {
        Stage stage = (Stage) reservationTable.getScene().getWindow();
        stage.close();
    }

    private void updateTableInfo() {
        int total = reservationTable.getItems().size();
        long pending = reservationTable.getItems().stream()
                .filter(r -> "En attente".equals(r.getStatus()))
                .count();

        String info = "Affichage de " + total + " réservation" + (total > 1 ? "s" : "");
        if (pending > 0) {
            info += " (" + pending + " en attente)";
        }
        tableInfoLabel.setText(info);
    }

    // Utility methods for dialogs
    private void showSuccessDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarningDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Display class for table rows
    public static class ReservationDisplay {
        private final Long id;
        private final String salleName;
        private final String userName;
        private final String dateDebut;
        private final String dateFin;
        private final String duration;
        private final String status;
        private final String description;
        private final LocalDateTime dateDebutDateTime;
        private final LocalDateTime dateFinDateTime;

        public ReservationDisplay(Reservation reservation) {
            this.id = reservation.getId();
            this.salleName = reservation.getSalle().getNom();
            this.userName = reservation.getUtilisateur().getNom() + " (" + reservation.getUtilisateur().getEmail() + ")";

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            this.dateDebut = reservation.getDateDebut().format(formatter);
            this.dateFin = reservation.getDateFin().format(formatter);
            this.dateDebutDateTime = reservation.getDateDebut();
            this.dateFinDateTime = reservation.getDateFin();

            // Calculate duration
            long hours = ChronoUnit.HOURS.between(reservation.getDateDebut(), reservation.getDateFin());
            long minutes = ChronoUnit.MINUTES.between(reservation.getDateDebut(), reservation.getDateFin()) % 60;
            this.duration = hours + "h" + (minutes > 0 ? String.format("%02dm", minutes) : "");

            // Status based on reservation status and time
            switch (reservation.getStatut()) {
                case EN_ATTENTE:
                    this.status = "En attente";
                    break;
                case REJETEE:
                    this.status = "Rejetée";
                    break;
                case ANNULEE:
                    this.status = "Annulée";
                    break;
                case APPROUVEE:
                    // For approved reservations, check current time status
                    LocalDateTime now = LocalDateTime.now();
                    if (reservation.getDateDebut().isAfter(now)) {
                        this.status = "À venir";
                    } else if (reservation.getDateFin().isBefore(now)) {
                        this.status = "Terminée";
                    } else {
                        this.status = "En cours";
                    }
                    break;
                default:
                    this.status = "Inconnue";
            }

            this.description = reservation.getDescription() != null ? reservation.getDescription() : "";
        }

        // Getters
        public Long getId() { return id; }
        public String getSalleName() { return salleName; }
        public String getUserName() { return userName; }
        public String getDateDebut() { return dateDebut; }
        public String getDateFin() { return dateFin; }
        public String getDuration() { return duration; }
        public String getStatus() { return status; }
        public String getDescription() { return description; }
        public LocalDateTime getDateDebutDateTime() { return dateDebutDateTime; }
        public LocalDateTime getDateFinDateTime() { return dateFinDateTime; }
    }
}