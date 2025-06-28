package com.my.project.service;

import com.my.project.model.Reservation;
import com.my.project.model.Utilisateur;
import com.my.project.model.Salle;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class AnalyticsService {

    private final ReservationService reservationService;
    private final UserService userService;
    private final SalleService salleService;
    private final EquipementService equipementService;

    public AnalyticsService() {
        this.reservationService = new ReservationService();
        this.userService = new UserService();
        this.salleService = new SalleService();
        this.equipementService = new EquipementService();
    }

    /**
     * Get comprehensive analytics data for specified period
     */
    public AnalyticsData getAnalyticsData(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Get reservations for the period
            List<Reservation> reservations = getReservationsForPeriod(session, startDate, endDate);

            // Calculate key metrics
            int totalReservations = reservations.size();
            double avgDuration = calculateAverageDuration(reservations);
            double occupancyRate = calculateOccupancyRate(reservations);
            String topUser = getTopUser(reservations);
            int topUserCount = getTopUserReservationCount(reservations);

            // Calculate trends (comparing with previous period)
            String reservationsTrend = calculateReservationsTrend(session, startDate, days);
            String durationTrend = calculateDurationTrend(session, startDate, days);
            String occupancyTrend = calculateOccupancyTrend(session, startDate, days);

            // Get chart data
            Map<String, Integer> reservationsOverTime = getReservationsOverTime(reservations, days);
            Map<String, Integer> roomUsageStats = getRoomUsageStats(reservations);
            Map<String, Integer> peakHoursStats = getPeakHoursStats(reservations);
            Map<String, Integer> equipmentStats = getEquipmentStats(session);

            // Get top users and rooms
            List<UserAnalyticData> topUsers = getTopUsers(reservations);
            List<RoomAnalyticData> popularRooms = getPopularRooms(reservations);

            // Get summary statistics
            int confirmedReservations = totalReservations; // Assuming all are confirmed for now
            int cancelledReservations = getCancelledReservations(session, startDate, endDate);
            double confirmationRate = calculateConfirmationRate(confirmedReservations, cancelledReservations);

            String peakHour = getPeakHour(reservations);
            String busiestDay = getBusiestDay(reservations);
            int totalDuration = calculateTotalDuration(reservations);

            String mostUsedRoom = getMostUsedRoom(reservations);
            String mostRequestedEquipment = equipementService.getMostRequestedEquipment();
            double avgCapacityUsed = calculateAverageCapacityUsed(reservations);

            int activeUsers = getActiveUsersCount(session, startDate, endDate);
            int newUsers = getNewUsersCount(session, startDate, endDate);
            double avgReservationsPerUser = calculateAvgReservationsPerUser(reservations);

            return new AnalyticsData(
                    totalReservations, reservationsTrend, avgDuration, durationTrend,
                    occupancyRate, occupancyTrend, topUser, topUserCount,
                    reservationsOverTime, roomUsageStats, peakHoursStats, equipmentStats,
                    topUsers, popularRooms,
                    confirmedReservations, cancelledReservations, confirmationRate,
                    peakHour, busiestDay, totalDuration,
                    mostUsedRoom, mostRequestedEquipment, avgCapacityUsed,
                    activeUsers, newUsers, avgReservationsPerUser
            );

        } catch (Exception e) {
            System.err.println("Error getting analytics data: " + e.getMessage());
            return getEmptyAnalyticsData();
        }
    }

    private List<Reservation> getReservationsForPeriod(Session session, LocalDateTime start, LocalDateTime end) {
        Query<Reservation> query = session.createQuery(
                "FROM Reservation r WHERE r.dateDebut >= :start AND r.dateDebut <= :end ORDER BY r.dateDebut",
                Reservation.class);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return query.list();
    }

    private double calculateAverageDuration(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;

        return reservations.stream()
                .mapToLong(r -> ChronoUnit.HOURS.between(r.getDateDebut(), r.getDateFin()))
                .average()
                .orElse(0.0);
    }

    private double calculateOccupancyRate(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;

        // Calculate total reserved hours vs total available hours
        long totalReservedHours = reservations.stream()
                .mapToLong(r -> ChronoUnit.HOURS.between(r.getDateDebut(), r.getDateFin()))
                .sum();

        // Assuming 8 hours per day available time for each room
        int totalRooms = salleService.getTotalRoomsCount();
        long totalAvailableHours = totalRooms * 8L * (reservations.size() > 0 ?
                ChronoUnit.DAYS.between(
                        reservations.get(0).getDateDebut().toLocalDate(),
                        reservations.get(reservations.size()-1).getDateDebut().toLocalDate()
                ) + 1 : 1);

        return totalAvailableHours > 0 ? (double) totalReservedHours / totalAvailableHours * 100 : 0.0;
    }

    private String getTopUser(List<Reservation> reservations) {
        if (reservations.isEmpty()) return "Aucun";

        return reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getUtilisateur().getNom(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Aucun");
    }

    private int getTopUserReservationCount(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0;

        return reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getUtilisateur().getNom(), Collectors.counting()))
                .values().stream()
                .mapToInt(Long::intValue)
                .max()
                .orElse(0);
    }

    private String calculateReservationsTrend(Session session, LocalDateTime currentStart, int days) {
        try {
            LocalDateTime previousStart = currentStart.minusDays(days);
            LocalDateTime previousEnd = currentStart;

            Query<Long> query = session.createQuery(
                    "SELECT COUNT(r) FROM Reservation r WHERE r.dateDebut >= :start AND r.dateDebut < :end",
                    Long.class);
            query.setParameter("start", previousStart);
            query.setParameter("end", previousEnd);

            long previousCount = query.uniqueResult();
            long currentCount = getReservationsForPeriod(session, currentStart, LocalDateTime.now()).size();

            if (previousCount == 0) return "Nouveau";

            double percentChange = ((double) currentCount - previousCount) / previousCount * 100;
            return String.format("%+.0f%% vs période précédente", percentChange);
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String calculateDurationTrend(Session session, LocalDateTime currentStart, int days) {
        try {
            LocalDateTime previousStart = currentStart.minusDays(days);
            LocalDateTime previousEnd = currentStart;

            List<Reservation> previousReservations = getReservationsForPeriod(session, previousStart, previousEnd);
            List<Reservation> currentReservations = getReservationsForPeriod(session, currentStart, LocalDateTime.now());

            double previousAvg = calculateAverageDuration(previousReservations);
            double currentAvg = calculateAverageDuration(currentReservations);

            if (previousAvg == 0) return "Nouveau";

            double change = currentAvg - previousAvg;
            return String.format("%+.1fh vs période précédente", change);
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String calculateOccupancyTrend(Session session, LocalDateTime currentStart, int days) {
        try {
            LocalDateTime previousStart = currentStart.minusDays(days);
            LocalDateTime previousEnd = currentStart;

            List<Reservation> previousReservations = getReservationsForPeriod(session, previousStart, previousEnd);
            List<Reservation> currentReservations = getReservationsForPeriod(session, currentStart, LocalDateTime.now());

            double previousRate = calculateOccupancyRate(previousReservations);
            double currentRate = calculateOccupancyRate(currentReservations);

            double change = currentRate - previousRate;
            return String.format("%+.1f%% vs période précédente", change);
        } catch (Exception e) {
            return "N/A";
        }
    }

    private Map<String, Integer> getReservationsOverTime(List<Reservation> reservations, int days) {
        Map<String, Integer> result = new LinkedHashMap<>();
        DateTimeFormatter formatter = days <= 7 ?
                DateTimeFormatter.ofPattern("dd/MM") :
                DateTimeFormatter.ofPattern("dd/MM");

        // Group reservations by date
        Map<String, Long> reservationsByDate = reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDateDebut().format(formatter),
                        Collectors.counting()
                ));

        // Fill in missing dates with 0
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        for (int i = 0; i < days; i++) {
            String dateStr = start.plusDays(i).format(formatter);
            result.put(dateStr, reservationsByDate.getOrDefault(dateStr, 0L).intValue());
        }

        return result;
    }

    private Map<String, Integer> getRoomUsageStats(List<Reservation> reservations) {
        return reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSalle().getType() != null ? r.getSalle().getType() : "Non spécifié",
                        Collectors.counting()
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().intValue()
                ));
    }

    private Map<String, Integer> getPeakHoursStats(List<Reservation> reservations) {
        Map<String, Integer> hourCounts = new TreeMap<>();

        for (Reservation r : reservations) {
            int hour = r.getDateDebut().getHour();
            String hourRange = hour + "h-" + (hour + 1) + "h";
            hourCounts.merge(hourRange, 1, Integer::sum);
        }

        return hourCounts;
    }

    private Map<String, Integer> getEquipmentStats(Session session) {
        try {
            Query<Object[]> query = session.createQuery(
                    "SELECT e.nom, COUNT(s) FROM Equipement e " +
                            "JOIN Salle s ON e MEMBER OF s.equipements " +
                            "GROUP BY e.nom ORDER BY COUNT(s) DESC",
                    Object[].class);
            query.setMaxResults(10);

            return query.list().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row[0],
                            row -> ((Long) row[1]).intValue(),
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<UserAnalyticData> getTopUsers(List<Reservation> reservations) {
        return reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getUtilisateur))
                .entrySet().stream()
                .map(entry -> {
                    Utilisateur user = entry.getKey();
                    List<Reservation> userReservations = entry.getValue();
                    int count = userReservations.size();
                    long totalHours = userReservations.stream()
                            .mapToLong(r -> ChronoUnit.HOURS.between(r.getDateDebut(), r.getDateFin()))
                            .sum();

                    return new UserAnalyticData(user.getNom(), count, (int) totalHours, user.getRole());
                })
                .sorted((a, b) -> Integer.compare(b.getReservationCount(), a.getReservationCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<RoomAnalyticData> getPopularRooms(List<Reservation> reservations) {
        Map<Salle, List<Reservation>> roomReservations = reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getSalle));

        return roomReservations.entrySet().stream()
                .map(entry -> {
                    Salle room = entry.getKey();
                    List<Reservation> roomRes = entry.getValue();
                    int count = roomRes.size();

                    // Calculate occupancy rate for this room
                    long totalHours = roomRes.stream()
                            .mapToLong(r -> ChronoUnit.HOURS.between(r.getDateDebut(), r.getDateFin()))
                            .sum();

                    // Assuming 8 hours per day available
                    long availableHours = 8L * 30; // Approximate for last month
                    double occupancyRate = availableHours > 0 ? (double) totalHours / availableHours * 100 : 0;

                    return new RoomAnalyticData(room.getNom(), count, (int) occupancyRate,
                            room.getType() != null ? room.getType() : "Non spécifié");
                })
                .sorted((a, b) -> Integer.compare(b.getReservationCount(), a.getReservationCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private int getCancelledReservations(Session session, LocalDateTime start, LocalDateTime end) {
        // Since we don't have a status field, return 0 for now
        // You can add a status field to Reservation entity later
        return 0;
    }

    private double calculateConfirmationRate(int confirmed, int cancelled) {
        int total = confirmed + cancelled;
        return total > 0 ? (double) confirmed / total * 100 : 100;
    }

    private String getPeakHour(List<Reservation> reservations) {
        if (reservations.isEmpty()) return "N/A";

        Map<Integer, Long> hourCounts = reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDateDebut().getHour(),
                        Collectors.counting()
                ));

        int peakHour = hourCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);

        return peakHour + "h-" + (peakHour + 1) + "h";
    }

    private String getBusiestDay(List<Reservation> reservations) {
        if (reservations.isEmpty()) return "N/A";

        Map<String, Long> dayCounts = reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDateDebut().getDayOfWeek().toString(),
                        Collectors.counting()
                ));

        return dayCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> translateDayOfWeek(entry.getKey()))
                .orElse("N/A");
    }

    private int calculateTotalDuration(List<Reservation> reservations) {
        return (int) reservations.stream()
                .mapToLong(r -> ChronoUnit.HOURS.between(r.getDateDebut(), r.getDateFin()))
                .sum();
    }

    private String getMostUsedRoom(List<Reservation> reservations) {
        if (reservations.isEmpty()) return "Aucune";

        return reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getSalle().getNom(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Aucune");
    }

    private double calculateAverageCapacityUsed(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;

        // This would require knowing how many people attended each reservation
        // For now, return a mock percentage
        return 65.0;
    }

    private int getActiveUsersCount(Session session, LocalDateTime start, LocalDateTime end) {
        try {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(DISTINCT r.utilisateur) FROM Reservation r " +
                            "WHERE r.dateDebut >= :start AND r.dateDebut <= :end",
                    Long.class);
            query.setParameter("start", start);
            query.setParameter("end", end);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            return 0;
        }
    }

    private int getNewUsersCount(Session session, LocalDateTime start, LocalDateTime end) {
        // This would require a creation date field in User entity
        // For now, return a mock value
        return 8;
    }

    private double calculateAvgReservationsPerUser(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;

        long uniqueUsers = reservations.stream()
                .map(Reservation::getUtilisateur)
                .distinct()
                .count();

        return uniqueUsers > 0 ? (double) reservations.size() / uniqueUsers : 0.0;
    }

    private String translateDayOfWeek(String dayOfWeek) {
        switch (dayOfWeek) {
            case "MONDAY": return "Lundi";
            case "TUESDAY": return "Mardi";
            case "WEDNESDAY": return "Mercredi";
            case "THURSDAY": return "Jeudi";
            case "FRIDAY": return "Vendredi";
            case "SATURDAY": return "Samedi";
            case "SUNDAY": return "Dimanche";
            default: return dayOfWeek;
        }
    }

    private AnalyticsData getEmptyAnalyticsData() {
        return new AnalyticsData(
                0, "N/A", 0.0, "N/A", 0.0, "N/A", "Aucun", 0,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(),
                0, 0, 0.0, "N/A", "N/A", 0,
                "Aucune", "Aucun", 0.0, 0, 0, 0.0
        );
    }

    /**
     * Export analytics data to PDF or Excel
     */
    public String exportAnalytics(String format, int days) throws Exception {
        AnalyticsData data = getAnalyticsData(days);

        if ("PDF".equals(format)) {
            return exportAnalyticsToPDF(data, days);
        } else {
            return exportAnalyticsToExcel(data, days);
        }
    }

    private String exportAnalyticsToPDF(AnalyticsData data, int days) throws Exception {
        // Use the existing ReportService to generate analytics PDF
        ReportService reportService = new ReportService();
        return reportService.generatePDFReport("analytics_" + days + "_jours");
    }

    private String exportAnalyticsToExcel(AnalyticsData data, int days) throws Exception {
        // Use the existing ReportService to generate analytics Excel
        ReportService reportService = new ReportService();
        return reportService.generateExcelReport("analytics_" + days + "_jours");
    }

    // Data classes
    public static class AnalyticsData {
        private final int totalReservations;
        private final String reservationsTrend;
        private final double averageDuration;
        private final String durationTrend;
        private final double occupancyRate;
        private final String occupancyTrend;
        private final String topUser;
        private final int topUserCount;

        private final Map<String, Integer> reservationsOverTime;
        private final Map<String, Integer> roomUsageStats;
        private final Map<String, Integer> peakHoursStats;
        private final Map<String, Integer> equipmentStats;

        private final List<UserAnalyticData> topUsers;
        private final List<RoomAnalyticData> popularRooms;

        private final int confirmedReservations;
        private final int cancelledReservations;
        private final double confirmationRate;
        private final String peakHour;
        private final String busiestDay;
        private final int totalDuration;
        private final String mostUsedRoom;
        private final String mostRequestedEquipment;
        private final double avgCapacityUsed;
        private final int activeUsers;
        private final int newUsers;
        private final double avgReservationsPerUser;

        public AnalyticsData(int totalReservations, String reservationsTrend, double averageDuration,
                             String durationTrend, double occupancyRate, String occupancyTrend,
                             String topUser, int topUserCount,
                             Map<String, Integer> reservationsOverTime, Map<String, Integer> roomUsageStats,
                             Map<String, Integer> peakHoursStats, Map<String, Integer> equipmentStats,
                             List<UserAnalyticData> topUsers, List<RoomAnalyticData> popularRooms,
                             int confirmedReservations, int cancelledReservations, double confirmationRate,
                             String peakHour, String busiestDay, int totalDuration,
                             String mostUsedRoom, String mostRequestedEquipment, double avgCapacityUsed,
                             int activeUsers, int newUsers, double avgReservationsPerUser) {
            this.totalReservations = totalReservations;
            this.reservationsTrend = reservationsTrend;
            this.averageDuration = averageDuration;
            this.durationTrend = durationTrend;
            this.occupancyRate = occupancyRate;
            this.occupancyTrend = occupancyTrend;
            this.topUser = topUser;
            this.topUserCount = topUserCount;
            this.reservationsOverTime = reservationsOverTime;
            this.roomUsageStats = roomUsageStats;
            this.peakHoursStats = peakHoursStats;
            this.equipmentStats = equipmentStats;
            this.topUsers = topUsers;
            this.popularRooms = popularRooms;
            this.confirmedReservations = confirmedReservations;
            this.cancelledReservations = cancelledReservations;
            this.confirmationRate = confirmationRate;
            this.peakHour = peakHour;
            this.busiestDay = busiestDay;
            this.totalDuration = totalDuration;
            this.mostUsedRoom = mostUsedRoom;
            this.mostRequestedEquipment = mostRequestedEquipment;
            this.avgCapacityUsed = avgCapacityUsed;
            this.activeUsers = activeUsers;
            this.newUsers = newUsers;
            this.avgReservationsPerUser = avgReservationsPerUser;
        }

        // Getters
        public int getTotalReservations() { return totalReservations; }
        public String getReservationsTrend() { return reservationsTrend; }
        public double getAverageDuration() { return averageDuration; }
        public String getDurationTrend() { return durationTrend; }
        public double getOccupancyRate() { return occupancyRate; }
        public String getOccupancyTrend() { return occupancyTrend; }
        public String getTopUser() { return topUser; }
        public int getTopUserCount() { return topUserCount; }
        public Map<String, Integer> getReservationsOverTime() { return reservationsOverTime; }
        public Map<String, Integer> getRoomUsageStats() { return roomUsageStats; }
        public Map<String, Integer> getPeakHoursStats() { return peakHoursStats; }
        public Map<String, Integer> getEquipmentStats() { return equipmentStats; }
        public List<UserAnalyticData> getTopUsers() { return topUsers; }
        public List<RoomAnalyticData> getPopularRooms() { return popularRooms; }
        public int getConfirmedReservations() { return confirmedReservations; }
        public int getCancelledReservations() { return cancelledReservations; }
        public double getConfirmationRate() { return confirmationRate; }
        public String getPeakHour() { return peakHour; }
        public String getBusiestDay() { return busiestDay; }
        public int getTotalDuration() { return totalDuration; }
        public String getMostUsedRoom() { return mostUsedRoom; }
        public String getMostRequestedEquipment() { return mostRequestedEquipment; }
        public double getAvgCapacityUsed() { return avgCapacityUsed; }
        public int getActiveUsers() { return activeUsers; }
        public int getNewUsers() { return newUsers; }
        public double getAvgReservationsPerUser() { return avgReservationsPerUser; }
    }

    public static class UserAnalyticData {
        private final String name;
        private final int reservationCount;
        private final int totalHours;
        private final String role;

        public UserAnalyticData(String name, int reservationCount, int totalHours, String role) {
            this.name = name;
            this.reservationCount = reservationCount;
            this.totalHours = totalHours;
            this.role = role;
        }

        public String getName() { return name; }
        public int getReservationCount() { return reservationCount; }
        public int getTotalHours() { return totalHours; }
        public String getRole() { return role; }
    }

    public static class RoomAnalyticData {
        private final String name;
        private final int reservationCount;
        private final int occupancyRate;
        private final String type;

        public RoomAnalyticData(String name, int reservationCount, int occupancyRate, String type) {
            this.name = name;
            this.reservationCount = reservationCount;
            this.occupancyRate = occupancyRate;
            this.type = type;
        }

        public String getName() { return name; }
        public int getReservationCount() { return reservationCount; }
        public int getOccupancyRate() { return occupancyRate; }
        public String getType() { return type; }
    }
}