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
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class MyReservationsController implements Initializable {

    // Filters and search
    @FXML private ComboBox<String> periodFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField roomFilter;

    // Statistics
    @FXML private Label userInfoLabel;
    @FXML private Label totalReservationsLabel;
    @FXML private Label upcomingReservationsLabel;
    @FXML private Label activeReservationsLabel;
    @FXML private Label completedReservationsLabel;
    @FXML private Label totalHoursLabel;

    // Table
    @FXML private TableView<ReservationDisplay> reservationTable;
    @FXML private TableColumn<ReservationDisplay, String> statusCol;
    @FXML private TableColumn<ReservationDisplay, String> salleCol;
    @FXML private TableColumn<ReservationDisplay, String> dateDebutCol;
    @FXML private TableColumn<ReservationDisplay, String> dateFinCol;
    @FXML private TableColumn<ReservationDisplay, String> durationCol;
    @FXML private TableColumn<ReservationDisplay, String> descCol;
    @FXML private TableColumn<ReservationDisplay, String> capacityCol;
    @FXML private TableColumn<ReservationDisplay, String> equipmentCol;
    @FXML private TableColumn<ReservationDisplay, String> reminderCol;
    @FXML private TableColumn<ReservationDisplay, Void> actionsCol;

    // Info labels
    @FXML private Label tableInfoLabel;
    @FXML private Label selectionInfoLabel;

    private final ReservationService reservationService;
    private ObservableList<ReservationDisplay> allReservations;
    private Utilisateur utilisateur;

    public MyReservationsController() {
        this.reservationService = new ReservationService();
        this.allReservations = FXCollections.observableArrayList();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFilters();
        setupTableColumns();
        setupTableSelection();
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        System.err.println("Utilisateur Connecter MyReservation: "+utilisateur);
        this.utilisateur = utilisateur;
        userInfoLabel.setText("Utilisateur: " + utilisateur.getNom());
        loadReservations();
        loadStatistics();
    }

    private void setupFilters() {
        // Period filter
        ObservableList<String> periodOptions = FXCollections.observableArrayList(
                "Toutes les périodes", "Aujourd'hui", "Cette semaine", "Ce mois", "À venir", "Historique"
        );
        periodFilter.setItems(periodOptions);
        periodFilter.setValue("Toutes les périodes");

        // Status filter - Updated with approval statuses
        ObservableList<String> statusOptions = FXCollections.observableArrayList(
                "Tous les statuts", "En attente", "Approuvées", "Rejetées", "À venir", "En cours", "Terminée"
        );
        statusFilter.setItems(statusOptions);
        statusFilter.setValue("Tous les statuts");

        // Real-time room filter
        roomFilter.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() > 1 || newText.isEmpty()) {
                Platform.runLater(this::handleFilter);
            }
        });
    }

    private void setupTableColumns() {
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        salleCol.setCellValueFactory(new PropertyValueFactory<>("salleName"));
        dateDebutCol.setCellValueFactory(new PropertyValueFactory<>("dateDebut"));
        dateFinCol.setCellValueFactory(new PropertyValueFactory<>("dateFin"));
        durationCol.setCellValueFactory(new PropertyValueFactory<>("duration"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        capacityCol.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        equipmentCol.setCellValueFactory(new PropertyValueFactory<>("equipment"));
        reminderCol.setCellValueFactory(new PropertyValueFactory<>("reminder"));

        // Setup actions column
        setupActionsColumn();

        // Setup row styling based on status
        reservationTable.setRowFactory(tv -> {
            TableRow<ReservationDisplay> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.getStyleClass().removeAll("row-pending", "row-approved", "row-rejected", "row-upcoming", "row-active", "row-completed");
                } else {
                    row.getStyleClass().removeAll("row-pending", "row-approved", "row-rejected", "row-upcoming", "row-active", "row-completed");
                    switch (newItem.getStatus()) {
                        case "En attente":
                            row.getStyleClass().add("row-pending");
                            break;
                        case "Rejetée":
                            row.getStyleClass().add("row-rejected");
                            break;
                        case "À venir":
                            row.getStyleClass().add("row-upcoming");
                            break;
                        case "En cours":
                            row.getStyleClass().add("row-active");
                            break;
                        case "Terminée":
                            row.getStyleClass().add("row-completed");
                            break;
                    }
                }
            });
            return row;
        });

        // Enhanced status column with colors and icons
        statusCol.setCellFactory(column -> new TableCell<ReservationDisplay, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    switch (status) {
                        case "En attente":
                            setText("⏳ En attente");
                            setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                            break;
                        case "Rejetée":
                            setText("❌ Rejetée");
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                            break;
                        case "À venir":
                            setText("📅 À venir");
                            setStyle("-fx-text-fill: #3b82f6; -fx-font-weight: bold;");
                            break;
                        case "En cours":
                            setText("🟢 En cours");
                            setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                            break;
                        case "Terminée":
                            setText("✅ Terminée");
                            setStyle("-fx-text-fill: #6b7280; -fx-font-weight: normal;");
                            break;
                        default:
                            setText(status);
                            setStyle("");
                    }
                }
            }
        });
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(param -> new TableCell<ReservationDisplay, Void>() {
            private final Button editBtn = new Button("✏️");
            private final Button cancelBtn = new Button("❌");
            private final Button viewBtn = new Button("👁️");

            {
                editBtn.getStyleClass().add("table-action-button");
                cancelBtn.getStyleClass().add("table-action-button");
                viewBtn.getStyleClass().add("table-action-button");
                editBtn.setTooltip(new Tooltip("Modifier"));
                cancelBtn.setTooltip(new Tooltip("Annuler"));
                viewBtn.setTooltip(new Tooltip("Voir détails"));

                editBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleModifierReservation(item);
                });

                cancelBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleAnnulerReservation(item);
                });

                viewBtn.setOnAction(e -> {
                    ReservationDisplay item = getTableView().getItems().get(getIndex());
                    handleViewDetails(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReservationDisplay reservation = getTableView().getItems().get(getIndex());
                    HBox buttons = new HBox(5);

                    switch (reservation.getStatus()) {
                        case "En attente":
                            // Pending: only view details and cancel
                            buttons.getChildren().addAll(viewBtn, cancelBtn);
                            break;
                        case "Rejetée":
                            // Rejected: only view details
                            buttons.getChildren().add(viewBtn);
                            break;
                        case "À venir":
                            // Approved and future: edit and cancel
                            buttons.getChildren().addAll(editBtn, cancelBtn);
                            break;
                        case "En cours":
                            // Active: only cancel
                            buttons.getChildren().addAll(viewBtn, cancelBtn);
                            break;
                        case "Terminée":
                            // Completed: only view
                            buttons.getChildren().add(viewBtn);
                            break;
                    }
                    setGraphic(buttons);
                }
            }
        });
    }

    private void handleViewDetails(ReservationDisplay reservationDisplay) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
            if (reservation == null) {
                showErrorDialog("Erreur", "Réservation introuvable.");
                return;
            }

            // Create details dialog
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Détails de la réservation");
            dialog.setHeaderText("Réservation #" + reservation.getId());

            VBox content = new VBox(10);
            content.getChildren().addAll(
                    new Label("Statut: " + getStatusText(reservation.getStatut())),
                    new Label("Salle: " + reservation.getSalle().getNom()),
                    new Label("Date début: " + reservation.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
                    new Label("Date fin: " + reservation.getDateFin().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
                    new Label("Description: " + (reservation.getDescription() != null ? reservation.getDescription() : "Aucune")),
                    new Label("Date de demande: " + (reservation.getDateCreation() != null ?
                            reservation.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "N/A"))
            );

            // Add admin validation info if available
            if (reservation.getDateValidation() != null) {
                content.getChildren().addAll(
                        new Label("Date de validation: " + reservation.getDateValidation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
                        new Label("Validé par: " + (reservation.getValidateurAdmin() != null ?
                                reservation.getValidateurAdmin().getNom() : "Système"))
                );
            }

            // Add admin comment if available
            if (reservation.getCommentaireAdmin() != null && !reservation.getCommentaireAdmin().trim().isEmpty()) {
                content.getChildren().addAll(
                        new Label("Commentaire admin:"),
                        new TextArea(reservation.getCommentaireAdmin()) {{
                            setEditable(false);
                            setPrefRowCount(2);
                            setWrapText(true);
                        }}
                );
            }

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de l'affichage des détails.");
        }
    }

    private String getStatusText(Reservation.StatutReservation statut) {
        switch (statut) {
            case EN_ATTENTE: return "⏳ En attente d'approbation";
            case APPROUVEE: return "✅ Approuvée";
            case REJETEE: return "❌ Rejetée";
            case ANNULEE: return "🚫 Annulée";
            default: return statut.toString();
        }
    }

    private void setupTableSelection() {
        reservationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectionInfoLabel.setText(newSelection.getSalleName() + " - " + newSelection.getDateDebut());
            } else {
                selectionInfoLabel.setText("Aucune");
            }
        });
    }

    private void loadReservations() {
        Task<List<Reservation>> loadTask = new Task<List<Reservation>>() {
            @Override
            protected List<Reservation> call() throws Exception {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    return session.createQuery(
                                    "FROM Reservation r LEFT JOIN FETCH r.salle LEFT JOIN FETCH r.validateurAdmin " +
                                            "WHERE r.utilisateur = :user ORDER BY r.dateCreation DESC",
                                    Reservation.class)
                            .setParameter("user", utilisateur)
                            .list();
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
        Task<UserReservationStats> statsTask = new Task<UserReservationStats>() {
            @Override
            protected UserReservationStats call() throws Exception {
                LocalDateTime now = LocalDateTime.now();

                int total = allReservations.size();

                // Count by approval status
                int pending = (int) allReservations.stream()
                        .filter(r -> "En attente".equals(r.getStatus()))
                        .count();

                int approved = (int) allReservations.stream()
                        .filter(r -> "À venir".equals(r.getStatus()) || "En cours".equals(r.getStatus()) || "Terminée".equals(r.getStatus()))
                        .count();

                int upcoming = (int) allReservations.stream()
                        .filter(r -> "À venir".equals(r.getStatus()))
                        .count();

                int active = (int) allReservations.stream()
                        .filter(r -> "En cours".equals(r.getStatus()))
                        .count();

                int completed = (int) allReservations.stream()
                        .filter(r -> "Terminée".equals(r.getStatus()))
                        .count();

                // Calculate total hours for approved reservations only
                long totalMinutes = allReservations.stream()
                        .filter(r -> !"En attente".equals(r.getStatus()) && !"Rejetée".equals(r.getStatus()))
                        .mapToLong(r -> ChronoUnit.MINUTES.between(r.getDateDebutDateTime(), r.getDateFinDateTime()))
                        .sum();

                return new UserReservationStats(total, upcoming, active, completed, totalMinutes / 60.0);
            }

            @Override
            protected void succeeded() {
                UserReservationStats stats = getValue();
                Platform.runLater(() -> updateStatisticsUI(stats));
            }
        };

        Thread statsThread = new Thread(statsTask);
        statsThread.setDaemon(true);
        statsThread.start();
    }

    private void updateStatisticsUI(UserReservationStats stats) {
        totalReservationsLabel.setText(String.valueOf(stats.total));
        upcomingReservationsLabel.setText(String.valueOf(stats.upcoming));
        activeReservationsLabel.setText(String.valueOf(stats.active));
        completedReservationsLabel.setText(String.valueOf(stats.completed));
        totalHoursLabel.setText(String.format("%.1fh", stats.totalHours));
    }

    @FXML
    private void handleFilter() {
        String selectedPeriod = periodFilter.getValue();
        String selectedStatus = statusFilter.getValue();
        String roomText = roomFilter.getText().trim().toLowerCase();

        ObservableList<ReservationDisplay> filteredList = FXCollections.observableArrayList();
        LocalDateTime now = LocalDateTime.now();

        for (ReservationDisplay r : allReservations) {
            boolean matches = true;

            // Period filter
            if (selectedPeriod != null && !selectedPeriod.equals("Toutes les périodes")) {
                switch (selectedPeriod) {
                    case "Aujourd'hui":
                        if (!r.getDateDebutDateTime().toLocalDate().equals(LocalDate.now())) {
                            matches = false;
                        }
                        break;
                    case "Cette semaine":
                        LocalDate startOfWeek = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
                        LocalDate endOfWeek = startOfWeek.plusDays(6);
                        if (r.getDateDebutDateTime().toLocalDate().isBefore(startOfWeek) ||
                                r.getDateDebutDateTime().toLocalDate().isAfter(endOfWeek)) {
                            matches = false;
                        }
                        break;
                    case "Ce mois":
                        if (!r.getDateDebutDateTime().toLocalDate().getMonth().equals(LocalDate.now().getMonth())) {
                            matches = false;
                        }
                        break;
                    case "À venir":
                        if (!r.getDateDebutDateTime().isAfter(now)) {
                            matches = false;
                        }
                        break;
                    case "Historique":
                        if (!r.getDateFinDateTime().isBefore(now)) {
                            matches = false;
                        }
                        break;
                }
            }

            // Status filter
            if (selectedStatus != null && !selectedStatus.equals("Tous les statuts")) {
                if (selectedStatus.equals("Approuvées")) {
                    // Show all approved reservations (À venir, En cours, Terminée)
                    if (!("À venir".equals(r.getStatus()) || "En cours".equals(r.getStatus()) || "Terminée".equals(r.getStatus()))) {
                        matches = false;
                    }
                } else if (!r.getStatus().equals(selectedStatus)) {
                    matches = false;
                }
            }

            // Room filter
            if (!roomText.isEmpty() && !r.getSalleName().toLowerCase().contains(roomText)) {
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
        periodFilter.setValue("Toutes les périodes");
        statusFilter.setValue("Tous les statuts");
        roomFilter.clear();
        reservationTable.setItems(allReservations);
        updateTableInfo();
    }

    // Quick filter methods
    @FXML
    private void handleFilterToday() {
        periodFilter.setValue("Aujourd'hui");
        handleFilter();
    }

    @FXML
    private void handleFilterThisWeek() {
        periodFilter.setValue("Cette semaine");
        handleFilter();
    }

    @FXML
    private void handleFilterUpcoming() {
        periodFilter.setValue("À venir");
        handleFilter();
    }

    @FXML
    private void handleFilterHistory() {
        periodFilter.setValue("Historique");
        handleFilter();
    }

    @FXML
    private void handleRefresh() {
        loadReservations();
        loadStatistics();
    }

    @FXML
    private void handleNouvelleReservation() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/reservation.fxml"));
            Scene scene = new Scene(loader.load());

            // Pass current user to reservation controller
            Object controller = loader.getController();
            if (controller instanceof ReservationController) {
                ((ReservationController) controller).setUtilisateur(utilisateur);
            }

            Stage stage = new Stage();
            stage.setTitle("Nouvelle Réservation");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();

            // Refresh when reservation window is closed
            stage.setOnHidden(e -> handleRefresh());
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir la fenêtre de réservation.");
        }
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
        // Only allow modification of approved future reservations
        if (!"À venir".equals(reservationDisplay.getStatus())) {
            showWarningDialog("Modification impossible",
                    "Seules les réservations approuvées à venir peuvent être modifiées.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
            if (reservation == null) {
                showErrorDialog("Erreur", "Réservation introuvable.");
                return;
            }

            // Create comprehensive modification dialog
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Modifier la réservation");
            dialog.setHeaderText("Modification de: " + reservation.getSalle().getNom());

            // Form fields
            DatePicker datePicker = new DatePicker(reservation.getDateDebut().toLocalDate());
            TextField debutField = new TextField(reservation.getDateDebut().toLocalTime().toString());
            TextField finField = new TextField(reservation.getDateFin().toLocalTime().toString());
            TextArea descField = new TextArea(reservation.getDescription());
            descField.setPrefRowCount(3);

            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(15);
            grid.addRow(0, new Label("Date:"), datePicker);
            grid.addRow(1, new Label("Heure début:"), debutField);
            grid.addRow(2, new Label("Heure fin:"), finField);
            grid.addRow(3, new Label("Description:"), descField);

            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                updateReservation(reservation, datePicker.getValue(),
                        LocalTime.parse(debutField.getText()),
                        LocalTime.parse(finField.getText()),
                        descField.getText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de l'ouverture du dialogue de modification.");
        }
    }

    private void updateReservation(Reservation reservation, LocalDate date, LocalTime debut,
                                   LocalTime fin, String description) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            LocalDateTime dateDebut = LocalDateTime.of(date, debut);
            LocalDateTime dateFin = LocalDateTime.of(date, fin);

            // Validate times
            if (dateFin.isBefore(dateDebut) || dateFin.equals(dateDebut)) {
                showWarningDialog("Erreur de validation", "L'heure de fin doit être après l'heure de début.");
                return;
            }

            // Check for conflicts with other APPROVED reservations
            List<Reservation> conflicts = session.createQuery(
                            "FROM Reservation WHERE salle = :salle AND id <> :id AND statut = :statut AND " +
                                    "((:debut < dateFin AND :fin > dateDebut))", Reservation.class)
                    .setParameter("salle", reservation.getSalle())
                    .setParameter("id", reservation.getId())
                    .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                    .setParameter("debut", dateDebut)
                    .setParameter("fin", dateFin)
                    .list();

            if (!conflicts.isEmpty()) {
                showWarningDialog("Conflit détecté", "Ce créneau est déjà réservé pour cette salle.");
                return;
            }

            Transaction tx = session.beginTransaction();
            Reservation merged = session.merge(reservation);
            merged.setDateDebut(dateDebut);
            merged.setDateFin(dateFin);
            merged.setDescription(description);
            session.update(merged);
            tx.commit();

            loadReservations();
            showSuccessDialog("Succès", "Réservation modifiée avec succès.");

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de la modification: " + e.getMessage());
        }
    }

    @FXML
    private void handleAnnuler() {
        ReservationDisplay selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner une réservation à annuler.");
            return;
        }
        handleAnnulerReservation(selected);
    }

    private void handleAnnulerReservation(ReservationDisplay reservationDisplay) {
        // Check if reservation can be cancelled
        if ("Terminée".equals(reservationDisplay.getStatus()) || "Rejetée".equals(reservationDisplay.getStatus())) {
            showWarningDialog("Action impossible", "Impossible d'annuler une réservation terminée ou rejetée.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation d'annulation");
        confirmation.setHeaderText("Annuler la réservation");
        confirmation.setContentText("Êtes-vous sûr de vouloir annuler cette réservation ?\n\n" +
                "Salle: " + reservationDisplay.getSalleName() + "\n" +
                "Date: " + reservationDisplay.getDateDebut() + "\n" +
                "Statut: " + reservationDisplay.getStatus());

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                Reservation reservation = session.get(Reservation.class, reservationDisplay.getId());
                if (reservation != null) {
                    // For pending requests, delete the record
                    // For approved reservations, mark as cancelled
                    if (reservation.getStatut() == Reservation.StatutReservation.EN_ATTENTE) {
                        session.remove(reservation);
                    } else {
                        reservation.setStatut(Reservation.StatutReservation.ANNULEE);
                        session.update(reservation);
                    }
                    tx.commit();
                    loadReservations();
                    loadStatistics();
                    showSuccessDialog("Succès", "Réservation annulée avec succès.");
                } else {
                    showErrorDialog("Erreur", "Réservation introuvable.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog("Erreur", "Erreur lors de l'annulation: " + e.getMessage());
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

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Reservation original = session.get(Reservation.class, selected.getId());

            // Create duplication dialog
            Dialog<LocalDateTime> dialog = new Dialog<>();
            dialog.setTitle("Dupliquer la réservation");
            dialog.setHeaderText("Choisir la nouvelle date et heure");

            DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1));
            TextField heureField = new TextField(original.getDateDebut().toLocalTime().toString());

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.addRow(0, new Label("Nouvelle date:"), datePicker);
            grid.addRow(1, new Label("Heure début:"), heureField);

            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    try {
                        LocalTime time = LocalTime.parse(heureField.getText());
                        return LocalDateTime.of(datePicker.getValue(), time);
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            });

            Optional<LocalDateTime> result = dialog.showAndWait();
            if (result.isPresent()) {
                createDuplicateReservation(original, result.get());
            }
        }
    }

    private void createDuplicateReservation(Reservation original, LocalDateTime newStartTime) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            long duration = ChronoUnit.MINUTES.between(original.getDateDebut(), original.getDateFin());
            LocalDateTime newEndTime = newStartTime.plusMinutes(duration);

            // Check for conflicts with approved reservations
            List<Reservation> conflicts = session.createQuery(
                            "FROM Reservation WHERE salle = :salle AND statut = :statut AND " +
                                    "((:debut < dateFin AND :fin > dateDebut))", Reservation.class)
                    .setParameter("salle", original.getSalle())
                    .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                    .setParameter("debut", newStartTime)
                    .setParameter("fin", newEndTime)
                    .list();

            if (!conflicts.isEmpty()) {
                showWarningDialog("Conflit détecté", "Ce créneau est déjà réservé pour cette salle.");
                return;
            }

            // Create new request (status: EN_ATTENTE)
            Reservation duplicate = new Reservation();
            duplicate.setUtilisateur(original.getUtilisateur());
            duplicate.setSalle(original.getSalle());
            duplicate.setDateDebut(newStartTime);
            duplicate.setDateFin(newEndTime);
            duplicate.setDescription(original.getDescription() + " (Copie)");
            duplicate.setStatut(Reservation.StatutReservation.EN_ATTENTE);
            duplicate.setDateCreation(LocalDateTime.now());

            Transaction tx = session.beginTransaction();
            session.persist(duplicate);
            tx.commit();

            loadReservations();
            showSuccessDialog("Succès", "Demande de réservation dupliquée avec succès. Elle sera examinée par un administrateur.");

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de la duplication: " + e.getMessage());
        }
    }

    @FXML
    private void handleShare() {
        ReservationDisplay selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner une réservation à partager.");
            return;
        }

        // Create share dialog with reservation details
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Partager la réservation");
        dialog.setHeaderText("Détails de la réservation");

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Statut: " + selected.getStatus()),
                new Label("Salle: " + selected.getSalleName()),
                new Label("Date début: " + selected.getDateDebut()),
                new Label("Date fin: " + selected.getDateFin()),
                new Label("Durée: " + selected.getDuration()),
                new Label("Description: " + selected.getDescription()),
                new Label("Capacité: " + selected.getCapacity()),
                new Label(""),
                new Label("Copiez ces informations pour partager votre réservation.")
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter mes réservations");
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
                // You could create a specific method for user reservations
                if (file.getName().endsWith(".pdf")) {
                    return reportService.generatePDFReport("mes_reservations");
                } else {
                    return reportService.generateExcelReport("mes_reservations");
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

    // Data classes
    public static class ReservationDisplay {
        private final Long id;
        private final String status;
        private final String salleName;
        private final String dateDebut;
        private final String dateFin;
        private final String duration;
        private final String description;
        private final String capacity;
        private final String equipment;
        private final String reminder;
        private final LocalDateTime dateDebutDateTime;
        private final LocalDateTime dateFinDateTime;

        public ReservationDisplay(Reservation reservation) {
            this.id = reservation.getId();
            this.salleName = reservation.getSalle().getNom();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            this.dateDebut = reservation.getDateDebut().format(formatter);
            this.dateFin = reservation.getDateFin().format(formatter);
            this.dateDebutDateTime = reservation.getDateDebut();
            this.dateFinDateTime = reservation.getDateFin();

            // Calculate duration
            long hours = ChronoUnit.HOURS.between(reservation.getDateDebut(), reservation.getDateFin());
            long minutes = ChronoUnit.MINUTES.between(reservation.getDateDebut(), reservation.getDateFin()) % 60;
            this.duration = hours + "h" + (minutes > 0 ? String.format("%02dm", minutes) : "");

            // Determine status based on reservation status and time
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
            this.capacity = String.valueOf(reservation.getSalle().getCapacite()) + " pers.";

            // Get equipment list (simplified)
            this.equipment = getEquipmentList(reservation.getSalle());

            // Calculate reminder time
            LocalDateTime now = LocalDateTime.now();
            long minutesUntilStart = ChronoUnit.MINUTES.between(now, reservation.getDateDebut());
            if (minutesUntilStart > 0 && minutesUntilStart <= 60 && reservation.getStatut() == Reservation.StatutReservation.APPROUVEE) {
                this.reminder = minutesUntilStart + "min";
            } else {
                this.reminder = "-";
            }
        }

        private String getEquipmentList(Salle salle) {
            // This would ideally be done through a service to avoid N+1 queries
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                List<String> equipmentNames = session.createQuery(
                                "SELECT e.nom FROM Equipement e JOIN Salle s ON e MEMBER OF s.equipements WHERE s.id = :salleId",
                                String.class)
                        .setParameter("salleId", salle.getId())
                        .setMaxResults(3) // Limit for display
                        .list();

                if (equipmentNames.isEmpty()) {
                    return "Aucun";
                } else if (equipmentNames.size() <= 2) {
                    return String.join(", ", equipmentNames);
                } else {
                    return equipmentNames.get(0) + ", " + equipmentNames.get(1) + "...";
                }
            } catch (Exception e) {
                return "N/A";
            }
        }

        // Getters
        public Long getId() { return id; }
        public String getStatus() { return status; }
        public String getSalleName() { return salleName; }
        public String getDateDebut() { return dateDebut; }
        public String getDateFin() { return dateFin; }
        public String getDuration() { return duration; }
        public String getDescription() { return description; }
        public String getCapacity() { return capacity; }
        public String getEquipment() { return equipment; }
        public String getReminder() { return reminder; }
        public LocalDateTime getDateDebutDateTime() { return dateDebutDateTime; }
        public LocalDateTime getDateFinDateTime() { return dateFinDateTime; }
    }

    private static class UserReservationStats {
        final int total;
        final int upcoming;
        final int active;
        final int completed;
        final double totalHours;

        UserReservationStats(int total, int upcoming, int active, int completed, double totalHours) {
            this.total = total;
            this.upcoming = upcoming;
            this.active = active;
            this.completed = completed;
            this.totalHours = totalHours;
        }
    }
}