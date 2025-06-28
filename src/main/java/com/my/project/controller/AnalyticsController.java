package com.my.project.controller;

import com.my.project.model.Utilisateur;
import com.my.project.service.AnalyticsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.ResourceBundle;

public class AnalyticsController implements Initializable {

    // Key Metrics Labels
    @FXML private Label totalReservationsLabel;
    @FXML private Label reservationsTrendLabel;
    @FXML private Label avgDurationLabel;
    @FXML private Label durationTrendLabel;
    @FXML private Label occupancyRateLabel;
    @FXML private Label occupancyTrendLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topUserCountLabel;

    // Charts
    @FXML private LineChart<String, Number> reservationsLineChart;
    @FXML private PieChart roomUsagePieChart;
    @FXML private BarChart<String, Number> peakHoursBarChart;
    @FXML private BarChart<String, Number> equipmentPopularityChart;

    // Tables
    @FXML private TableView<UserAnalytics> topUsersTable;
    @FXML private TableColumn<UserAnalytics, String> userNameColumn;
    @FXML private TableColumn<UserAnalytics, Integer> userReservationsColumn;
    @FXML private TableColumn<UserAnalytics, String> userHoursColumn;
    @FXML private TableColumn<UserAnalytics, String> userRoleColumn;

    @FXML private TableView<RoomAnalytics> popularRoomsTable;
    @FXML private TableColumn<RoomAnalytics, String> roomNameColumn;
    @FXML private TableColumn<RoomAnalytics, Integer> roomReservationsColumn;
    @FXML private TableColumn<RoomAnalytics, String> roomOccupancyColumn;
    @FXML private TableColumn<RoomAnalytics, String> roomTypeColumn;

    // Summary Statistics
    @FXML private Label confirmedReservationsLabel;
    @FXML private Label cancelledReservationsLabel;
    @FXML private Label confirmationRateLabel;
    @FXML private Label peakHourLabel;
    @FXML private Label busiestDayLabel;
    @FXML private Label totalDurationLabel;
    @FXML private Label mostUsedRoomLabel;
    @FXML private Label mostRequestedEquipmentLabel;
    @FXML private Label avgCapacityUsedLabel;
    @FXML private Label activeUsersStatsLabel;
    @FXML private Label newUsersLabel;
    @FXML private Label avgReservationsPerUserLabel;

    // Controls
    @FXML private ComboBox<String> periodComboBox;

    private AnalyticsService analyticsService;
    private Utilisateur currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        analyticsService = new AnalyticsService();

        setupPeriodSelector();
        setupTableColumns();
        loadAnalytics();
    }

    public void setCurrentUser(Utilisateur user) {
        this.currentUser = user;
    }

    private void setupPeriodSelector() {
        ObservableList<String> periods = FXCollections.observableArrayList(
                "7 derniers jours",
                "30 derniers jours",
                "3 derniers mois",
                "6 derniers mois",
                "Cette année",
                "Année dernière"
        );
        periodComboBox.setItems(periods);
        periodComboBox.setValue("30 derniers jours");

        periodComboBox.setOnAction(e -> refreshAnalytics());
    }

    private void setupTableColumns() {
        // User table columns
        userNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        userReservationsColumn.setCellValueFactory(new PropertyValueFactory<>("reservationCount"));
        userHoursColumn.setCellValueFactory(new PropertyValueFactory<>("totalHours"));
        userRoleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));

        // Room table columns
        roomNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        roomReservationsColumn.setCellValueFactory(new PropertyValueFactory<>("reservationCount"));
        roomOccupancyColumn.setCellValueFactory(new PropertyValueFactory<>("occupancyRate"));
        roomTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    }

    @FXML
    private void refreshAnalytics() {
        loadAnalytics();
    }

    @FXML
    private void exportAnalytics() {
        try {
            // Create choice dialog for export format
            ChoiceDialog<String> dialog = new ChoiceDialog<>("PDF", "PDF", "Excel");
            dialog.setTitle("Exporter Analytics");
            dialog.setHeaderText("Sélectionnez le format d'exportation");
            dialog.setContentText("Format:");

            dialog.showAndWait().ifPresent(format -> {
                Task<String> exportTask = new Task<String>() {
                    @Override
                    protected String call() throws Exception {
                        return analyticsService.exportAnalytics(format, getPeriodDays());
                    }

                    @Override
                    protected void succeeded() {
                        Platform.runLater(() -> {
                            showSuccessDialog("Export réussi",
                                    "Les analytics ont été exportées:\n" + getValue());
                        });
                    }

                    @Override
                    protected void failed() {
                        Platform.runLater(() -> {
                            showErrorDialog("Erreur d'export",
                                    "Erreur lors de l'export: " + getException().getMessage());
                        });
                    }
                };

                Thread exportThread = new Thread(exportTask);
                exportThread.setDaemon(true);
                exportThread.start();
            });
        } catch (Exception e) {
            showErrorDialog("Erreur", "Impossible d'exporter les analytics: " + e.getMessage());
        }
    }

    private void loadAnalytics() {
        Task<Void> analyticsTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int days = getPeriodDays();

                // Load analytics data
                AnalyticsService.AnalyticsData data = analyticsService.getAnalyticsData(days);

                Platform.runLater(() -> updateUI(data));
                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showErrorDialog("Erreur de chargement",
                            "Erreur lors du chargement des analytics: " + getException().getMessage());
                });
            }
        };

        Thread analyticsThread = new Thread(analyticsTask);
        analyticsThread.setDaemon(true);
        analyticsThread.start();
    }

    private void updateUI(AnalyticsService.AnalyticsData data) {
        // Update key metrics
        updateKeyMetrics(data);

        // Update charts
        updateCharts(data);

        // Update tables
        updateTables(data);

        // Update summary statistics
        updateSummaryStatistics(data);
    }

    private void updateKeyMetrics(AnalyticsService.AnalyticsData data) {
        totalReservationsLabel.setText(String.valueOf(data.getTotalReservations()));
        reservationsTrendLabel.setText(data.getReservationsTrend());

        avgDurationLabel.setText(String.format("%.1fh", data.getAverageDuration()));
        durationTrendLabel.setText(data.getDurationTrend());

        occupancyRateLabel.setText(String.format("%.0f%%", data.getOccupancyRate()));
        occupancyTrendLabel.setText(data.getOccupancyTrend());

        topUserLabel.setText(data.getTopUser());
        topUserCountLabel.setText(data.getTopUserCount() + " réservations");

        // Set trend styles
        setTrendStyle(reservationsTrendLabel, data.getReservationsTrend());
        setTrendStyle(durationTrendLabel, data.getDurationTrend());
        setTrendStyle(occupancyTrendLabel, data.getOccupancyTrend());
    }

    private void updateCharts(AnalyticsService.AnalyticsData data) {
        // Reservations over time
        updateReservationsChart(data.getReservationsOverTime());

        // Room usage pie chart
        updateRoomUsageChart(data.getRoomUsageStats());

        // Peak hours bar chart
        updatePeakHoursChart(data.getPeakHoursStats());

        // Equipment popularity chart
        updateEquipmentChart(data.getEquipmentStats());
    }

    private void updateReservationsChart(Map<String, Integer> data) {
        reservationsLineChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Réservations");

        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        reservationsLineChart.getData().add(series);
    }

    private void updateRoomUsageChart(Map<String, Integer> data) {
        roomUsagePieChart.getData().clear();
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
        }

        roomUsagePieChart.setData(pieData);
    }

    private void updatePeakHoursChart(Map<String, Integer> data) {
        peakHoursBarChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Réservations");

        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        peakHoursBarChart.getData().add(series);
    }

    private void updateEquipmentChart(Map<String, Integer> data) {
        equipmentPopularityChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Demandes");

        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        equipmentPopularityChart.getData().add(series);
    }

    private void updateTables(AnalyticsService.AnalyticsData data) {
        // Update top users table
        ObservableList<UserAnalytics> userAnalytics = FXCollections.observableArrayList();
        for (AnalyticsService.UserAnalyticData userData : data.getTopUsers()) {
            userAnalytics.add(new UserAnalytics(
                    userData.getName(),
                    userData.getReservationCount(),
                    userData.getTotalHours() + "h",
                    userData.getRole()
            ));
        }
        topUsersTable.setItems(userAnalytics);

        // Update popular rooms table
        ObservableList<RoomAnalytics> roomAnalytics = FXCollections.observableArrayList();
        for (AnalyticsService.RoomAnalyticData roomData : data.getPopularRooms()) {
            roomAnalytics.add(new RoomAnalytics(
                    roomData.getName(),
                    roomData.getReservationCount(),
                    roomData.getOccupancyRate() + "%",
                    roomData.getType()
            ));
        }
        popularRoomsTable.setItems(roomAnalytics);
    }

    private void updateSummaryStatistics(AnalyticsService.AnalyticsData data) {
        confirmedReservationsLabel.setText(String.valueOf(data.getConfirmedReservations()));
        cancelledReservationsLabel.setText(String.valueOf(data.getCancelledReservations()));
        confirmationRateLabel.setText(String.format("%.0f%%", data.getConfirmationRate()));

        peakHourLabel.setText(data.getPeakHour());
        busiestDayLabel.setText(data.getBusiestDay());
        totalDurationLabel.setText(data.getTotalDuration() + "h");

        mostUsedRoomLabel.setText(data.getMostUsedRoom());
        mostRequestedEquipmentLabel.setText(data.getMostRequestedEquipment());
        avgCapacityUsedLabel.setText(String.format("%.0f%%", data.getAvgCapacityUsed()));

        activeUsersStatsLabel.setText(String.valueOf(data.getActiveUsers()));
        newUsersLabel.setText(String.valueOf(data.getNewUsers()));
        avgReservationsPerUserLabel.setText(String.format("%.1f", data.getAvgReservationsPerUser()));
    }

    private void setTrendStyle(Label label, String trend) {
        label.getStyleClass().removeAll("positive", "negative", "neutral");
        if (trend.contains("+")) {
            label.getStyleClass().add("positive");
        } else if (trend.contains("-")) {
            label.getStyleClass().add("negative");
        } else {
            label.getStyleClass().add("neutral");
        }
    }

    private int getPeriodDays() {
        String period = periodComboBox.getValue();
        switch (period) {
            case "7 derniers jours": return 7;
            case "30 derniers jours": return 30;
            case "3 derniers mois": return 90;
            case "6 derniers mois": return 180;
            case "Cette année": return (int) LocalDate.now().getDayOfYear();
            case "Année dernière": return 365;
            default: return 30;
        }
    }

    private void showSuccessDialog(String title, String message) {
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

    // Data classes for tables
    public static class UserAnalytics {
        private final String name;
        private final Integer reservationCount;
        private final String totalHours;
        private final String role;

        public UserAnalytics(String name, Integer reservationCount, String totalHours, String role) {
            this.name = name;
            this.reservationCount = reservationCount;
            this.totalHours = totalHours;
            this.role = role;
        }

        public String getName() { return name; }
        public Integer getReservationCount() { return reservationCount; }
        public String getTotalHours() { return totalHours; }
        public String getRole() { return role; }
    }

    public static class RoomAnalytics {
        private final String name;
        private final Integer reservationCount;
        private final String occupancyRate;
        private final String type;

        public RoomAnalytics(String name, Integer reservationCount, String occupancyRate, String type) {
            this.name = name;
            this.reservationCount = reservationCount;
            this.occupancyRate = occupancyRate;
            this.type = type;
        }

        public String getName() { return name; }
        public Integer getReservationCount() { return reservationCount; }
        public String getOccupancyRate() { return occupancyRate; }
        public String getType() { return type; }
    }
}