package com.my.project.service;

import com.my.project.model.Reservation;
import com.my.project.model.Salle;
import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReservationService extends BaseService<Reservation> {

    public ReservationService() {
        super(Reservation.class);
    }

    /**
     * Get count of active reservations (current and future)
     */
    public int getActiveReservationsCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(r) FROM Reservation r WHERE r.dateFin >= :now", Long.class);
            query.setParameter("now", LocalDateTime.now());
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting active reservations count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get count of reservations for today
     */
    public int getTodayReservationsCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            Query<Long> query = session.createQuery(
                    "SELECT COUNT(r) FROM Reservation r WHERE r.dateDebut >= :start AND r.dateDebut < :end",
                    Long.class);
            query.setParameter("start", startOfDay);
            query.setParameter("end", endOfDay);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting today's reservations count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get reservations by user
     */
    public List<Reservation> getReservationsByUser(Utilisateur user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Reservation> query = session.createQuery(
                    "FROM Reservation r WHERE r.utilisateur = :user ORDER BY r.dateDebut DESC",
                    Reservation.class);
            query.setParameter("user", user);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting reservations by user: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get reservations by salle
     */
    public List<Reservation> getReservationsBySalle(Salle salle) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Reservation> query = session.createQuery(
                    "FROM Reservation r WHERE r.salle = :salle ORDER BY r.dateDebut DESC",
                    Reservation.class);
            query.setParameter("salle", salle);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting reservations by salle: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get upcoming reservations
     */
    public List<Reservation> getUpcomingReservations(int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Reservation> query = session.createQuery(
                    "FROM Reservation r WHERE r.dateDebut > :now ORDER BY r.dateDebut ASC",
                    Reservation.class);
            query.setParameter("now", LocalDateTime.now());
            query.setMaxResults(limit);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting upcoming reservations: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Check if salle is available for the given time period
     */
    public boolean isSalleAvailable(Salle salle, LocalDateTime debut, LocalDateTime fin) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(r) FROM Reservation r WHERE r.salle = :salle " +
                            "AND ((r.dateDebut <= :debut AND r.dateFin > :debut) " +
                            "OR (r.dateDebut < :fin AND r.dateFin >= :fin) " +
                            "OR (r.dateDebut >= :debut AND r.dateFin <= :fin))",
                    Long.class);
            query.setParameter("salle", salle);
            query.setParameter("debut", debut);
            query.setParameter("fin", fin);
            return query.uniqueResult() == 0;
        } catch (Exception e) {
            System.err.println("Error checking salle availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get occupancy rate for all salles
     */
    public double getOccupancyRate() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Calculate total hours reserved today
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            Query<Object[]> query = session.createQuery(
                    "SELECT SUM(TIMESTAMPDIFF(HOUR, r.dateDebut, r.dateFin)), COUNT(DISTINCT r.salle) " +
                            "FROM Reservation r WHERE r.dateDebut >= :start AND r.dateDebut < :end",
                    Object[].class);
            query.setParameter("start", startOfDay);
            query.setParameter("end", endOfDay);

            Object[] result = query.uniqueResult();
            if (result != null && result[0] != null && result[1] != null) {
                double totalReservedHours = ((Number) result[0]).doubleValue();
                int usedRooms = ((Number) result[1]).intValue();

                // Assuming 8 working hours per day per room
                double totalAvailableHours = usedRooms * 8;
                if (totalAvailableHours > 0) {
                    return (totalReservedHours / totalAvailableHours) * 100;
                }
            }
            return 0.0;
        } catch (Exception e) {
            System.err.println("Error calculating occupancy rate: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Get monthly reservation statistics
     */
    public Map<String, Integer> getMonthlyStats() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Object[]> query = session.createQuery(
                    "SELECT MONTH(r.dateDebut), COUNT(r) " +
                            "FROM Reservation r " +
                            "WHERE YEAR(r.dateDebut) = YEAR(CURRENT_DATE) " +
                            "GROUP BY MONTH(r.dateDebut)",
                    Object[].class);

            return query.list().stream()
                    .collect(Collectors.toMap(
                            row -> getMonthName((Integer) row[0]),
                            row -> ((Long) row[1]).intValue()
                    ));
        } catch (Exception e) {
            System.err.println("Error getting monthly statistics: " + e.getMessage());
            return Map.of();
        }
    }

    private String getMonthName(int month) {
        String[] months = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
        return months[month];
    }

    /**
     * Get pending requests count (assuming you might add a status field later)
     */
    public int getPendingRequestsCount() {
        // For now, return upcoming reservations as "pending"
        return getUpcomingReservations(100).size();
    }
}