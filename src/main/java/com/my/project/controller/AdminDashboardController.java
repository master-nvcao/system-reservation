package com.my.project.controller;

import com.my.project.model.Utilisateur;
import com.my.project.service.DashboardService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class AdminDashboardController implements Initializable {

    @FXML private Label adminWelcomeLabel;
    @FXML private Label activeUsersCount;
    @FXML private Label availableRoomsCount;
    @FXML private Label activeReservationsCount;
    @FXML private Label pendingRequestsCount;

    // Dashboard Cards
    @FXML private VBox usersCard;
    @FXML private VBox roomsCard;
    @FXML private VBox equipmentCard;
    @FXML private VBox reservationsCard;

    // Services
    private DashboardService dashboardService;
    private Utilisateur utilisateur;

    public AdminDashboardController() {
        try {
            this.dashboardService = new DashboardService();
        } catch (Exception e) {
            System.err.println("Error initializing DashboardService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupCardClickHandlers();
        // Load dashboard data after UI is initialized
        Platform.runLater(this::loadDashboardData);
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
        if (adminWelcomeLabel != null) {
            adminWelcomeLabel.setText(utilisateur.getNom());
        }

        // Log user login and load dashboard data
        if (dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Dashboard Access");
        }
        loadDashboardData();
    }

    private void setupCardClickHandlers() {
        if (usersCard != null) {
            usersCard.setOnMouseClicked(this::handleUsersCardClick);
        }
        if (roomsCard != null) {
            roomsCard.setOnMouseClicked(this::handleRoomsCardClick);
        }
        if (equipmentCard != null) {
            equipmentCard.setOnMouseClicked(this::handleEquipmentCardClick);
        }
        if (reservationsCard != null) {
            reservationsCard.setOnMouseClicked(this::handleReservationsCardClick);
        }
    }

    private void loadDashboardData() {
        if (dashboardService == null) {
            // Set default values if service is not available
            updateStatisticsWithDefaults();
            return;
        }

        // Load data in background to avoid blocking UI
        Task<Map<String, Integer>> loadDataTask = new Task<Map<String, Integer>>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                return dashboardService.getBasicStats();
            }

            @Override
            protected void succeeded() {
                Map<String, Integer> stats = getValue();
                Platform.runLater(() -> updateStatistics(stats));
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    System.err.println("Failed to load dashboard data: " + getException().getMessage());
                    updateStatisticsWithDefaults();
                });
            }
        };

        // Run the task in a background thread
        Thread backgroundThread = new Thread(loadDataTask);
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void updateStatistics(Map<String, Integer> stats) {
        if (activeUsersCount != null) {
            activeUsersCount.setText(String.valueOf(stats.getOrDefault("activeUsers", 0)));
        }
        if (availableRoomsCount != null) {
            availableRoomsCount.setText(String.valueOf(stats.getOrDefault("availableRooms", 0)));
        }
        if (activeReservationsCount != null) {
            activeReservationsCount.setText(String.valueOf(stats.getOrDefault("activeReservations", 0)));
        }
        if (pendingRequestsCount != null) {
            pendingRequestsCount.setText(String.valueOf(stats.getOrDefault("pendingRequests", 0)));
        }
    }

    private void updateStatisticsWithDefaults() {
        // Set default values when service is unavailable
        if (activeUsersCount != null) activeUsersCount.setText("0");
        if (availableRoomsCount != null) availableRoomsCount.setText("0");
        if (activeReservationsCount != null) activeReservationsCount.setText("0");
        if (pendingRequestsCount != null) pendingRequestsCount.setText("0");
    }

    // Card click handlers
    private void handleUsersCardClick(MouseEvent event) {
        if (utilisateur != null && dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Accessed User Management");
        }
        handleUserManagement();
    }

    private void handleRoomsCardClick(MouseEvent event) {
        if (utilisateur != null && dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Accessed Room Management");
        }
        handleRoomManagement();
    }

    private void handleEquipmentCardClick(MouseEvent event) {
        if (utilisateur != null && dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Accessed Equipment Management");
        }
        handleEquipementManagement();
    }

    private void handleReservationsCardClick(MouseEvent event) {
        if (utilisateur != null && dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Accessed Reservations View");
        }
        handleAllReservations();
    }

    // Original navigation methods (preserved from your existing controller)
    @FXML
    private void handleUserManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_management.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Gestion des utilisateurs");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();

            // Pass current user to the new controller if it supports it
            Object controller = loader.getController();
            if (controller instanceof UserAwareController && utilisateur != null) {
                ((UserAwareController) controller).setCurrentUser(utilisateur);
            }

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir la gestion des utilisateurs: " + e.getMessage());
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
            stage.setResizable(true);
            stage.centerOnScreen();

            // Pass current user to the new controller if it supports it
            Object controller = loader.getController();
            if (controller instanceof UserAwareController && utilisateur != null) {
                ((UserAwareController) controller).setCurrentUser(utilisateur);
            }

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir la gestion des salles: " + e.getMessage());
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
            stage.setResizable(true);
            stage.centerOnScreen();

            // Pass current user to the new controller if it supports it
            Object controller = loader.getController();
            if (controller instanceof UserAwareController && utilisateur != null) {
                ((UserAwareController) controller).setCurrentUser(utilisateur);
            }

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir la gestion des équipements: " + e.getMessage());
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
            stage.setResizable(true);
            stage.centerOnScreen();

            // Pass current user to the new controller if it supports it
            Object controller = loader.getController();
            if (controller instanceof UserAwareController && utilisateur != null) {
                ((UserAwareController) controller).setCurrentUser(utilisateur);
            }

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir les réservations: " + e.getMessage());
        }
    }

    // New navigation methods for the modern UI
    @FXML
    private void handleAnalytics() {
        if (utilisateur != null && dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Accessed Analytics");
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/analytics.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Analyse & Rapports Détaillés");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();

            // Pass current user to analytics controller
            Object controller = loader.getController();
            if (controller instanceof AnalyticsController) {
                ((AnalyticsController) controller).setCurrentUser(utilisateur);
            }

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Impossible d'ouvrir la page d'analyse: " + e.getMessage());
        }
    }

    @FXML
    private void handleReports() {
        if (utilisateur != null && dashboardService != null) {
            dashboardService.logUserActivity(utilisateur, "Accessed Report Generation");
        }

        try {
            // Show report selection dialog
            showReportSelectionDialog();
        } catch (Exception e) {
            showErrorDialog("Erreur", "Impossible d'accéder aux rapports: " + e.getMessage());
        }
    }

    private void showReportSelectionDialog() {
        // Create choice dialog for report type
        javafx.scene.control.ChoiceDialog<String> reportTypeDialog =
                new javafx.scene.control.ChoiceDialog<>("complet",
                        "complet", "reservations", "utilisateurs", "salles", "equipements");
        reportTypeDialog.setTitle("Génération de Rapport");
        reportTypeDialog.setHeaderText("Sélectionnez le type de rapport");
        reportTypeDialog.setContentText("Type de rapport:");

        java.util.Optional<String> reportTypeResult = reportTypeDialog.showAndWait();

        if (reportTypeResult.isPresent()) {
            String reportType = reportTypeResult.get();

            // Create choice dialog for format
            javafx.scene.control.ChoiceDialog<String> formatDialog =
                    new javafx.scene.control.ChoiceDialog<>("PDF", "PDF", "Excel");
            formatDialog.setTitle("Format de Rapport");
            formatDialog.setHeaderText("Sélectionnez le format de sortie");
            formatDialog.setContentText("Format:");

            java.util.Optional<String> formatResult = formatDialog.showAndWait();

            if (formatResult.isPresent()) {
                String format = formatResult.get();
                generateReport(reportType, format);
            }
        }
    }

    private void generateReport(String reportType, String format) {
        // Show progress dialog
        javafx.scene.control.Alert progressAlert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Génération en cours");
        progressAlert.setHeaderText("Génération du rapport en cours...");
        progressAlert.setContentText("Veuillez patienter pendant la génération du rapport " + format + ".");

        // Create background task
        Task<String> reportTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                com.my.project.service.ReportService reportService =
                        new com.my.project.service.ReportService();

                if ("PDF".equals(format)) {
                    return reportService.generatePDFReport(reportType);
                } else {
                    return reportService.generateExcelReport(reportType);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    progressAlert.close();
                    String fileName = getValue();
                    showSuccessDialog("Rapport généré",
                            "Le rapport a été généré avec succès:\n" + fileName);

                    // Log successful report generation
                    if (utilisateur != null && dashboardService != null) {
                        dashboardService.logUserActivity(utilisateur,
                                "Generated " + format + " report: " + reportType);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressAlert.close();
                    Throwable exception = getException();
                    showErrorDialog("Erreur de génération",
                            "Erreur lors de la génération du rapport:\n" +
                                    (exception != null ? exception.getMessage() : "Erreur inconnue"));
                });
            }
        };

        // Show progress and start task
        progressAlert.show();
        Thread reportThread = new Thread(reportTask);
        reportThread.setDaemon(true);
        reportThread.start();

        // Close progress dialog after a short delay if task completes quickly
        Timeline timeline = new Timeline(new KeyFrame(
                javafx.util.Duration.seconds(0.5),
                e -> {
                    if (reportTask.isDone() && !reportTask.isCancelled()) {
                        progressAlert.close();
                    }
                }
        ));
        timeline.play();
    }

    @FXML
    private void handleLogout() {
        // Confirm logout
        boolean confirmed = showConfirmationDialog("Déconnexion",
                "Êtes-vous sûr de vouloir vous déconnecter?");

        if (confirmed) {
            // Log the logout action
            if (utilisateur != null && dashboardService != null) {
                dashboardService.logUserActivity(utilisateur, "Logout");
                dashboardService.shutdown();
            }

            // Close current window
            Stage stage = (Stage) adminWelcomeLabel.getScene().getWindow();
            stage.close();

            // Optionally, reopen login window
            openLoginWindow();
        }
    }

    // Utility methods
    private void openLoginWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            Scene scene = new Scene(loader.load());

            Stage loginStage = new Stage();
            loginStage.setTitle("Connexion - Admin Hub");
            loginStage.setScene(scene);
            loginStage.setResizable(false);
            loginStage.centerOnScreen();
            loginStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccessDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    // Method to refresh dashboard data (can be called periodically or manually)
    public void refreshDashboard() {
        loadDashboardData();
    }

    // Interface for controllers that need user information
    public interface UserAwareController {
        void setCurrentUser(Utilisateur user);
    }
}