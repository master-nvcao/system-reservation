package com.my.project.service;

import com.my.project.model.Utilisateur;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardService {

    private final UserService userService;
    private final ReservationService reservationService;
    private final SalleService salleService;
    private final EquipementService equipementService;
    private final ExecutorService executorService;

    public DashboardService() {
        this.userService = new UserService();
        this.reservationService = new ReservationService();
        this.salleService = new SalleService();
        this.equipementService = new EquipementService();
        this.executorService = Executors.newFixedThreadPool(4);
    }

    /**
     * Get all dashboard data asynchronously
     */
    public CompletableFuture<DashboardData> getDashboardDataAsync() {
        CompletableFuture<UserService.UserStats> userStatsFuture =
                CompletableFuture.supplyAsync(() -> userService.getUserStats(), executorService);

        CompletableFuture<SalleService.RoomStats> roomStatsFuture =
                CompletableFuture.supplyAsync(() -> salleService.getRoomStats(), executorService);

        CompletableFuture<EquipementService.EquipmentStats> equipmentStatsFuture =
                CompletableFuture.supplyAsync(() -> equipementService.getEquipmentStats(), executorService);

        CompletableFuture<ReservationData> reservationDataFuture =
                CompletableFuture.supplyAsync(this::getReservationData, executorService);

        return CompletableFuture.allOf(userStatsFuture, roomStatsFuture, equipmentStatsFuture, reservationDataFuture)
                .thenApply(v -> new DashboardData(
                        userStatsFuture.join(),
                        roomStatsFuture.join(),
                        equipmentStatsFuture.join(),
                        reservationDataFuture.join(),
                        LocalDateTime.now()
                ));
    }

    /**
     * Get dashboard data synchronously (for simple use cases)
     */
    public DashboardData getDashboardData() {
        try {
            UserService.UserStats userStats = userService.getUserStats();
            SalleService.RoomStats roomStats = salleService.getRoomStats();
            EquipementService.EquipmentStats equipmentStats = equipementService.getEquipmentStats();
            ReservationData reservationData = getReservationData();

            return new DashboardData(userStats, roomStats, equipmentStats, reservationData, LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("Error getting dashboard data: " + e.getMessage());
            return getEmptyDashboardData();
        }
    }

    /**
     * Get basic statistics for quick dashboard update
     */
    public Map<String, Integer> getBasicStats() {
        Map<String, Integer> stats = new HashMap<>();
        try {
            stats.put("activeUsers", userService.getActiveUsersCount());
            stats.put("availableRooms", salleService.getAvailableRoomsCount());
            stats.put("activeReservations", reservationService.getActiveReservationsCount());
            stats.put("pendingRequests", reservationService.getPendingRequestsCount());
        } catch (Exception e) {
            System.err.println("Error getting basic stats: " + e.getMessage());
            // Return default values on error
            stats.put("activeUsers", 0);
            stats.put("availableRooms", 0);
            stats.put("activeReservations", 0);
            stats.put("pendingRequests", 0);
        }
        return stats;
    }

    private ReservationData getReservationData() {
        try {
            int activeReservations = reservationService.getActiveReservationsCount();
            int todayReservations = reservationService.getTodayReservationsCount();
            int pendingRequests = reservationService.getPendingRequestsCount();
            double occupancyRate = reservationService.getOccupancyRate();
            Map<String, Integer> monthlyStats = reservationService.getMonthlyStats();

            return new ReservationData(activeReservations, todayReservations, pendingRequests,
                    occupancyRate, monthlyStats);
        } catch (Exception e) {
            System.err.println("Error getting reservation data: " + e.getMessage());
            return new ReservationData(0, 0, 0, 0.0, Map.of());
        }
    }

    private DashboardData getEmptyDashboardData() {
        return new DashboardData(
                new UserService.UserStats(0, 0, 0, 0),
                new SalleService.RoomStats(0, 0, 0, 0.0, java.util.List.of()),
                new EquipementService.EquipmentStats(0, 0, 0, Map.of(), "N/A"),
                new ReservationData(0, 0, 0, 0.0, Map.of()),
                LocalDateTime.now()
        );
    }

    /**
     * Log user activity
     */
    public void logUserActivity(Utilisateur user, String action) {
        try {
            String logMessage = String.format("[%s] User: %s %s (ID: %d, Role: %s) - Action: %s",
                    LocalDateTime.now(),
                    user.getNom(),
                    user.getEmail(),
                    user.getId(),
                    user.getRole(),
                    action);
            System.out.println(logMessage);

            // Here you could save to a log file or audit table
            // auditService.logActivity(user, action, LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("Error logging user activity: " + e.getMessage());
        }
    }

    /**
     * Get popular items summary
     */
    public PopularItemsSummary getPopularItems() {
        try {
            String mostPopularRoom = salleService.getMostPopularRoom();
            String mostRequestedEquipment = equipementService.getMostRequestedEquipment();

            return new PopularItemsSummary(mostPopularRoom, mostRequestedEquipment);
        } catch (Exception e) {
            System.err.println("Error getting popular items: " + e.getMessage());
            return new PopularItemsSummary("N/A", "N/A");
        }
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        executorService.shutdown();
    }

    // Data classes for organizing dashboard information

    public static class DashboardData {
        private final UserService.UserStats userStats;
        private final SalleService.RoomStats roomStats;
        private final EquipementService.EquipmentStats equipmentStats;
        private final ReservationData reservationData;
        private final LocalDateTime lastUpdated;

        public DashboardData(UserService.UserStats userStats, SalleService.RoomStats roomStats,
                             EquipementService.EquipmentStats equipmentStats, ReservationData reservationData,
                             LocalDateTime lastUpdated) {
            this.userStats = userStats;
            this.roomStats = roomStats;
            this.equipmentStats = equipmentStats;
            this.reservationData = reservationData;
            this.lastUpdated = lastUpdated;
        }

        public UserService.UserStats getUserStats() { return userStats; }
        public SalleService.RoomStats getRoomStats() { return roomStats; }
        public EquipementService.EquipmentStats getEquipmentStats() { return equipmentStats; }
        public ReservationData getReservationData() { return reservationData; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
    }

    public static class ReservationData {
        private final int activeReservations;
        private final int todayReservations;
        private final int pendingRequests;
        private final double occupancyRate;
        private final Map<String, Integer> monthlyStats;

        public ReservationData(int activeReservations, int todayReservations, int pendingRequests,
                               double occupancyRate, Map<String, Integer> monthlyStats) {
            this.activeReservations = activeReservations;
            this.todayReservations = todayReservations;
            this.pendingRequests = pendingRequests;
            this.occupancyRate = occupancyRate;
            this.monthlyStats = monthlyStats;
        }

        public int getActiveReservations() { return activeReservations; }
        public int getTodayReservations() { return todayReservations; }
        public int getPendingRequests() { return pendingRequests; }
        public double getOccupancyRate() { return occupancyRate; }
        public Map<String, Integer> getMonthlyStats() { return monthlyStats; }
    }

    public static class PopularItemsSummary {
        private final String mostPopularRoom;
        private final String mostRequestedEquipment;

        public PopularItemsSummary(String mostPopularRoom, String mostRequestedEquipment) {
            this.mostPopularRoom = mostPopularRoom;
            this.mostRequestedEquipment = mostRequestedEquipment;
        }

        public String getMostPopularRoom() { return mostPopularRoom; }
        public String getMostRequestedEquipment() { return mostRequestedEquipment; }
    }
}