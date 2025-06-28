package com.my.project.service;

import com.my.project.model.Salle;
import com.my.project.model.Equipement;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class SalleService extends BaseService<Salle> {

    public SalleService() {
        super(Salle.class);
    }

    /**
     * Get count of available salles
     */
    public int getAvailableRoomsCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(s) FROM Salle s WHERE s.disponible = true", Long.class);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting available rooms count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get total rooms count
     */
    public int getTotalRoomsCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(s) FROM Salle s", Long.class);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting total rooms count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get all available salles
     */
    public List<Salle> getAvailableSalles() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "FROM Salle s WHERE s.disponible = true ORDER BY s.nom", Salle.class);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting available salles: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get salles by type
     */
    public List<Salle> getSallesByType(String type) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "FROM Salle s WHERE s.type = :type ORDER BY s.nom", Salle.class);
            query.setParameter("type", type);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting salles by type: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get salles by minimum capacity
     */
    public List<Salle> getSallesByMinCapacity(int minCapacity) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "FROM Salle s WHERE s.capacite >= :minCapacity AND s.disponible = true ORDER BY s.capacite",
                    Salle.class);
            query.setParameter("minCapacity", minCapacity);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting salles by min capacity: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Find salle by name
     */
    public Optional<Salle> findByNom(String nom) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "FROM Salle s WHERE s.nom = :nom", Salle.class);
            query.setParameter("nom", nom);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error finding salle by name: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get salles with specific equipment
     */
    public List<Salle> getSallesWithEquipement(Equipement equipement) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "SELECT s FROM Salle s JOIN s.equipements e WHERE e = :equipement AND s.disponible = true",
                    Salle.class);
            query.setParameter("equipement", equipement);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting salles with equipment: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get most popular salle (most reserved)
     */
    public String getMostPopularRoom() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Object[]> query = session.createQuery(
                    "SELECT s.nom, COUNT(r) as reservationCount " +
                            "FROM Salle s JOIN Reservation r ON s = r.salle " +
                            "WHERE r.dateDebut >= :thirtyDaysAgo " +
                            "GROUP BY s.nom " +
                            "ORDER BY reservationCount DESC",
                    Object[].class);
            query.setParameter("thirtyDaysAgo", LocalDateTime.now().minusDays(30));
            query.setMaxResults(1);

            List<Object[]> results = query.list();
            if (!results.isEmpty()) {
                return (String) results.get(0)[0];
            }
            return "Aucune";
        } catch (Exception e) {
            System.err.println("Error getting most popular room: " + e.getMessage());
            return "N/A";
        }
    }

    /**
     * Get room utilization statistics
     */
    public RoomStats getRoomStats() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            int totalRooms = getTotalRoomsCount();
            int availableRooms = getAvailableRoomsCount();
            int occupiedRooms = totalRooms - availableRooms;

            // Get average capacity
            Query<Double> avgQuery = session.createQuery(
                    "SELECT AVG(s.capacite) FROM Salle s", Double.class);
            double averageCapacity = avgQuery.uniqueResult() != null ? avgQuery.uniqueResult() : 0.0;

            // Get room types count
            Query<Object[]> typeQuery = session.createQuery(
                    "SELECT s.type, COUNT(s) FROM Salle s GROUP BY s.type", Object[].class);
            List<Object[]> typeResults = typeQuery.list();

            return new RoomStats(totalRooms, availableRooms, occupiedRooms, averageCapacity, typeResults);
        } catch (Exception e) {
            System.err.println("Error getting room statistics: " + e.getMessage());
            return new RoomStats(0, 0, 0, 0.0, List.of());
        }
    }

    /**
     * Get salles available at specific time
     */
    public List<Salle> getAvailableSallesAtTime(LocalDateTime debut, LocalDateTime fin) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "SELECT s FROM Salle s WHERE s.disponible = true " +
                            "AND s NOT IN (" +
                            "    SELECT r.salle FROM Reservation r " +
                            "    WHERE ((r.dateDebut <= :debut AND r.dateFin > :debut) " +
                            "    OR (r.dateDebut < :fin AND r.dateFin >= :fin) " +
                            "    OR (r.dateDebut >= :debut AND r.dateFin <= :fin))" +
                            ") ORDER BY s.nom",
                    Salle.class);
            query.setParameter("debut", debut);
            query.setParameter("fin", fin);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting available salles at time: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Inner class for room statistics
     */
    public static class RoomStats {
        private final int totalRooms;
        private final int availableRooms;
        private final int occupiedRooms;
        private final double averageCapacity;
        private final List<Object[]> roomTypeStats;

        public RoomStats(int totalRooms, int availableRooms, int occupiedRooms,
                         double averageCapacity, List<Object[]> roomTypeStats) {
            this.totalRooms = totalRooms;
            this.availableRooms = availableRooms;
            this.occupiedRooms = occupiedRooms;
            this.averageCapacity = averageCapacity;
            this.roomTypeStats = roomTypeStats;
        }

        public int getTotalRooms() { return totalRooms; }
        public int getAvailableRooms() { return availableRooms; }
        public int getOccupiedRooms() { return occupiedRooms; }
        public double getAverageCapacity() { return averageCapacity; }
        public List<Object[]> getRoomTypeStats() { return roomTypeStats; }
        public double getOccupancyPercentage() {
            return totalRooms > 0 ? ((double) occupiedRooms / totalRooms) * 100 : 0.0;
        }
    }
}