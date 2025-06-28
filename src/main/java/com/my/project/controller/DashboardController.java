package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Utilisateur;
import com.my.project.service.ReservationService;
import com.my.project.service.UserService;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class DashboardController implements Initializable {

    // Welcome and user info
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Button notificationButton;

    // Statistics
    @FXML private Label myReservationsCount;
    @FXML private Label upcomingCount;
    @FXML private Label completedCount;
    @FXML private Label favoriteRoom;

    // Recent activity
    @FXML private VBox recentActivityContainer;

    private Utilisateur utilisateurConnecte;
    private ReservationService reservationService;
    private Thread notificationThread;
    private boolean notificationsEnabled = true;

    public DashboardController() {
        this.reservationService = new ReservationService();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupNotificationButton();
    }

    public void setUtilisateur(Utilisateur utilisateur) {

        System.err.println("Utilisateur Connecte Dashboar : "+utilisateur);
        this.utilisateurConnecte = utilisateur;

        // Update UI with user info
        welcomeLabel.setText("Bienvenue, " + utilisateur.getNom() + "!");
        roleLabel.setText(capitalizeRole(utilisateur.getRole()));

        // Load user dashboard data
        loadUserStatistics();
        loadRecentActivity();

        // Start notification monitoring
        lancerNotifications(utilisateur);
    }

    private void setupNotificationButton() {
        notificationButton.setOnAction(e -> toggleNotifications());
        updateNotificationButtonStyle();
    }

    private void toggleNotifications() {
        notificationsEnabled = !notificationsEnabled;
        updateNotificationButtonStyle();

        if (notificationsEnabled) {
            showInfoDialog("Notifications", "Les notifications sont maintenant activées.");
            if (utilisateurConnecte != null) {
                lancerNotifications(utilisateurConnecte);
            }
        } else {
            showInfoDialog("Notifications", "Les notifications sont maintenant désactivées.");
            if (notificationThread != null) {
                notificationThread.interrupt();
            }
        }
    }

    private void updateNotificationButtonStyle() {
        notificationButton.getStyleClass().removeAll("notification-active", "notification-inactive");
        if (notificationsEnabled) {
            notificationButton.getStyleClass().add("notification-active");
            notificationButton.setText("🔔");
        } else {
            notificationButton.getStyleClass().add("notification-inactive");
            notificationButton.setText("🔕");
        }
    }

    private void loadUserStatistics() {
        Task<UserStats> statsTask = new Task<UserStats>() {
            @Override
            protected UserStats call() throws Exception {
                List<Reservation> userReservations = reservationService.getReservationsByUser(utilisateurConnecte);

                int total = userReservations.size();
                LocalDateTime now = LocalDateTime.now();

                int upcoming = (int) userReservations.stream()
                        .filter(r -> r.getDateDebut().isAfter(now))
                        .count();

                int completed = (int) userReservations.stream()
                        .filter(r -> r.getDateFin().isBefore(now))
                        .count();

                String favorite = getMostUsedRoom(userReservations);

                return new UserStats(total, upcoming, completed, favorite);
            }

            @Override
            protected void succeeded() {
                UserStats stats = getValue();
                Platform.runLater(() -> updateStatisticsUI(stats));
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    System.err.println("Error loading user statistics: " + getException().getMessage());
                    updateStatisticsUI(new UserStats(0, 0, 0, "Aucune"));
                });
            }
        };

        Thread statsThread = new Thread(statsTask);
        statsThread.setDaemon(true);
        statsThread.start();
    }

    private void updateStatisticsUI(UserStats stats) {
        myReservationsCount.setText(String.valueOf(stats.total));
        upcomingCount.setText(String.valueOf(stats.upcoming));
        completedCount.setText(String.valueOf(stats.completed));
        favoriteRoom.setText(stats.favoriteRoom);
    }

    private String getMostUsedRoom(List<Reservation> reservations) {
        if (reservations.isEmpty()) return "Aucune";

        Map<String, Long> roomCounts = reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getSalle().getNom(), Collectors.counting()));

        return roomCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Aucune");
    }

    private void loadRecentActivity() {
        Task<List<Reservation>> activityTask = new Task<List<Reservation>>() {
            @Override
            protected List<Reservation> call() throws Exception {
                return reservationService.getReservationsByUser(utilisateurConnecte)
                        .stream()
                        .sorted((r1, r2) -> r2.getDateDebut().compareTo(r1.getDateDebut()))
                        .limit(5)
                        .collect(Collectors.toList());
            }

            @Override
            protected void succeeded() {
                List<Reservation> recentReservations = getValue();
                Platform.runLater(() -> updateRecentActivityUI(recentReservations));
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    System.err.println("Error loading recent activity: " + getException().getMessage());
                });
            }
        };

        Thread activityThread = new Thread(activityTask);
        activityThread.setDaemon(true);
        activityThread.start();
    }

    private void updateRecentActivityUI(List<Reservation> reservations) {
        recentActivityContainer.getChildren().clear();

        if (reservations.isEmpty()) {
            Label noActivity = new Label("Aucune activité récente");
            noActivity.getStyleClass().add("no-activity-label");
            recentActivityContainer.getChildren().add(noActivity);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        LocalDateTime now = LocalDateTime.now();

        for (Reservation reservation : reservations) {
            HBox activityItem = createActivityItem(reservation, now, formatter);
            recentActivityContainer.getChildren().add(activityItem);
        }
    }

    private HBox createActivityItem(Reservation reservation, LocalDateTime now, DateTimeFormatter formatter) {
        HBox item = new HBox(15);
        item.getStyleClass().add("activity-item");
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Status indicator
        Label statusIcon = new Label();
        statusIcon.getStyleClass().add("activity-status");

        String statusText;
        if (reservation.getDateDebut().isAfter(now)) {
            statusIcon.setText("🟡");
            statusText = "À venir";
        } else if (reservation.getDateFin().isBefore(now)) {
            statusIcon.setText("✅");
            statusText = "Terminée";
        } else {
            statusIcon.setText("🟢");
            statusText = "En cours";
        }

        // Activity details
        VBox details = new VBox(2);
        Label title = new Label(statusText + " - " + reservation.getSalle().getNom());
        title.getStyleClass().add("activity-title");

        Label time = new Label(reservation.getDateDebut().format(formatter) + " - " +
                reservation.getDateFin().format(formatter));
        time.getStyleClass().add("activity-time");

        details.getChildren().addAll(title, time);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // Timestamp
        Label timestamp = new Label(getRelativeTime(reservation.getDateDebut(), now));
        timestamp.getStyleClass().add("activity-timestamp");

        item.getChildren().addAll(statusIcon, details, spacer, timestamp);
        return item;
    }

    private String getRelativeTime(LocalDateTime reservationTime, LocalDateTime now) {
        long hoursDiff = java.time.temporal.ChronoUnit.HOURS.between(reservationTime, now);
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(reservationTime, now);

        if (Math.abs(hoursDiff) < 1) {
            return "À l'instant";
        } else if (Math.abs(hoursDiff) < 24) {
            return "Il y a " + Math.abs(hoursDiff) + "h";
        } else if (Math.abs(daysDiff) == 1) {
            return reservationTime.isAfter(now) ? "Demain" : "Hier";
        } else {
            return "Il y a " + Math.abs(daysDiff) + " jour" + (Math.abs(daysDiff) > 1 ? "s" : "");
        }
    }

    @FXML
    private void handleOpenReservation() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/reservation.fxml"));
            Scene scene = new Scene(loader.load());

            // Inject utilisateur into ReservationController
            ReservationController controller = loader.getController();
            controller.setUtilisateur(utilisateurConnecte); // ✅

            Stage stage = new Stage();
            stage.setTitle("Nouvelle Réservation");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleOpenMyReservations() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/my_reservations.fxml"));
            Scene scene = new Scene(loader.load());

            // Inject utilisateur into MyReservationsController
            MyReservationsController controller = loader.getController();
            controller.setUtilisateur(utilisateurConnecte); // ✅

            Stage stage = new Stage();
            stage.setTitle("Mes Réservations");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void handleOpenSuggestion() {
        openWindow("/view/suggestion.fxml", "Suggestion Intelligente", 700, 500, false);
    }

    @FXML
    private void handleOpenCalendar() {
        // Placeholder for calendar view
        showInfoDialog("Calendrier", "Vue calendrier - Fonctionnalité à venir");
    }

    private void openWindow(String fxmlPath, String title, int width, int height, boolean passUser) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(loader.load(), width, height);

            if (passUser) {
                Object controller = loader.getController();
                if (controller instanceof UserAwareController) {
                    ((UserAwareController) controller).setUtilisateur(utilisateurConnecte);
                }
            }

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir " + title);
        }
    }

    @FXML
    private void handleLogout() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Déconnexion");
        confirmation.setHeaderText("Confirmer la déconnexion");
        confirmation.setContentText("Êtes-vous sûr de vouloir vous déconnecter?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Stop notification thread
                if (notificationThread != null) {
                    notificationThread.interrupt();
                }

                // Close current stage
                Stage currentStage = (Stage) welcomeLabel.getScene().getWindow();
                currentStage.close();

                // Optionally reopen login window
                openLoginWindow();
            }
        });
    }

    private void openLoginWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            Scene scene = new Scene(loader.load());

            Stage loginStage = new Stage();
            loginStage.setTitle("Connexion - Student Hub");
            loginStage.setScene(scene);
            loginStage.setResizable(false);
            loginStage.centerOnScreen();
            loginStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void lancerNotifications(Utilisateur user) {
        if (!notificationsEnabled) return;

        // Stop previous thread if running
        if (notificationThread != null && notificationThread.isAlive()) {
            notificationThread.interrupt();
        }

        notificationThread = new Thread(() -> {
            while (notificationsEnabled && !Thread.currentThread().isInterrupted()) {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Reservation> upcomingReservations = session.createQuery(
                                    "FROM Reservation WHERE utilisateur = :u AND dateDebut BETWEEN :now AND :in15",
                                    Reservation.class)
                            .setParameter("u", user)
                            .setParameter("now", LocalDateTime.now())
                            .setParameter("in15", LocalDateTime.now().plusMinutes(15))
                            .list();

                    if (!upcomingReservations.isEmpty()) {
                        Platform.runLater(() -> showUpcomingReservationNotification(upcomingReservations.get(0)));
                    }

                    Thread.sleep(5 * 60 * 1000); // Check every 5 minutes
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        notificationThread.setDaemon(true);
        notificationThread.start();
    }

    private void showUpcomingReservationNotification(Reservation reservation) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Rappel de Réservation");
        alert.setHeaderText("🔔 Réservation imminente!");
        alert.setContentText(
                "Votre réservation commence bientôt:\n\n" +
                        "📍 Salle: " + reservation.getSalle().getNom() + "\n" +
                        "⏰ Début: " + reservation.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n" +
                        "⏱️ Durée: " + java.time.temporal.ChronoUnit.HOURS.between(reservation.getDateDebut(), reservation.getDateFin()) + "h"
        );

        // Make notification more visible
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/style/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("notification-dialog");

        alert.show();
    }

    private String capitalizeRole(String role) {
        if (role == null || role.isEmpty()) return "Utilisateur";
        return role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();
    }

    // Utility methods
    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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

    // Interface for controllers that need user information
    public interface UserAwareController {
        void setUtilisateur(Utilisateur user);
    }

    // Data class for user statistics
    private static class UserStats {
        final int total;
        final int upcoming;
        final int completed;
        final String favoriteRoom;

        UserStats(int total, int upcoming, int completed, String favoriteRoom) {
            this.total = total;
            this.upcoming = upcoming;
            this.completed = completed;
            this.favoriteRoom = favoriteRoom;
        }
    }
}