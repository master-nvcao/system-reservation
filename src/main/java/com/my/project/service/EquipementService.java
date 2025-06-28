package com.my.project.service;

import com.my.project.model.Equipement;
import com.my.project.model.Salle;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

public class EquipementService extends BaseService<Equipement> {

    public EquipementService() {
        super(Equipement.class);
    }

    /**
     * Get total equipement count
     */
    public int getTotalEquipmentCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(e) FROM Equipement e", Long.class);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting total equipment count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get available equipment count (equipment that's in available rooms)
     */
    public int getAvailableEquipmentCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(DISTINCT e) FROM Salle s JOIN s.equipements e WHERE s.disponible = true",
                    Long.class);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting available equipment count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get equipements by type
     */
    public List<Equipement> getEquipementsByType(String type) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Equipement> query = session.createQuery(
                    "FROM Equipement e WHERE e.type = :type ORDER BY e.nom", Equipement.class);
            query.setParameter("type", type);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting equipements by type: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Find equipement by name
     */
    public Optional<Equipement> findByNom(String nom) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Equipement> query = session.createQuery(
                    "FROM Equipement e WHERE e.nom = :nom", Equipement.class);
            query.setParameter("nom", nom);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error finding equipement by name: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all equipment types
     */
    public List<String> getAllEquipmentTypes() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<String> query = session.createQuery(
                    "SELECT DISTINCT e.type FROM Equipement e ORDER BY e.type", String.class);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting equipment types: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get salles that have specific equipement
     */
    public List<Salle> getSallesWithEquipement(Long equipementId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Salle> query = session.createQuery(
                    "SELECT s FROM Salle s JOIN s.equipements e WHERE e.id = :equipementId",
                    Salle.class);
            query.setParameter("equipementId", equipementId);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting salles with equipement: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get most requested equipment (most present in rooms)
     */
    public String getMostRequestedEquipment() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Object[]> query = session.createQuery(
                    "SELECT e.nom, COUNT(s) as salleCount " +
                            "FROM Equipement e JOIN Salle s ON e MEMBER OF s.equipements " +
                            "GROUP BY e.nom " +
                            "ORDER BY salleCount DESC",
                    Object[].class);
            query.setMaxResults(1);

            List<Object[]> results = query.list();
            if (!results.isEmpty()) {
                return (String) results.get(0)[0];
            }
            return "Aucun";
        } catch (Exception e) {
            System.err.println("Error getting most requested equipment: " + e.getMessage());
            return "N/A";
        }
    }

    /**
     * Get equipment usage statistics
     */
    public Map<String, Integer> getEquipmentUsageStats() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Object[]> query = session.createQuery(
                    "SELECT e.nom, COUNT(s) as usageCount " +
                            "FROM Equipement e JOIN Salle s ON e MEMBER OF s.equipements " +
                            "GROUP BY e.nom " +
                            "ORDER BY usageCount DESC",
                    Object[].class);

            return query.list().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row[0],
                            row -> ((Long) row[1]).intValue()
                    ));
        } catch (Exception e) {
            System.err.println("Error getting equipment usage statistics: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * Get equipment statistics by type
     */
    public Map<String, Integer> getEquipmentStatsByType() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Object[]> query = session.createQuery(
                    "SELECT e.type, COUNT(e) FROM Equipement e GROUP BY e.type", Object[].class);

            return query.list().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row[0],
                            row -> ((Long) row[1]).intValue()
                    ));
        } catch (Exception e) {
            System.err.println("Error getting equipment stats by type: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * Get unused equipment (not assigned to any room)
     */
    public List<Equipement> getUnusedEquipment() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Equipement> query = session.createQuery(
                    "SELECT e FROM Equipement e WHERE e NOT IN " +
                            "(SELECT DISTINCT eq FROM Salle s JOIN s.equipements eq)",
                    Equipement.class);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting unused equipment: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get pending equipment requests count
     * This is a placeholder - you might want to create a separate EquipmentRequest entity
     */
    public int getPendingEquipmentRequestsCount() {
        // For now, return count of unused equipment as "pending requests"
        return getUnusedEquipment().size();
    }

    /**
     * Search equipment by name or description
     */
    public List<Equipement> searchEquipment(String searchTerm) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Equipement> query = session.createQuery(
                    "FROM Equipement e WHERE LOWER(e.nom) LIKE LOWER(:searchTerm) " +
                            "OR LOWER(e.description) LIKE LOWER(:searchTerm) " +
                            "ORDER BY e.nom",
                    Equipement.class);
            query.setParameter("searchTerm", "%" + searchTerm + "%");
            return query.list();
        } catch (Exception e) {
            System.err.println("Error searching equipment: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get equipment statistics
     */
    public EquipmentStats getEquipmentStats() {
        try {
            int totalEquipment = getTotalEquipmentCount();
            int availableEquipment = getAvailableEquipmentCount();
            int unusedEquipment = getUnusedEquipment().size();
            Map<String, Integer> typeStats = getEquipmentStatsByType();
            String mostRequested = getMostRequestedEquipment();

            return new EquipmentStats(totalEquipment, availableEquipment, unusedEquipment,
                    typeStats, mostRequested);
        } catch (Exception e) {
            System.err.println("Error getting equipment statistics: " + e.getMessage());
            return new EquipmentStats(0, 0, 0, Map.of(), "N/A");
        }
    }

    /**
     * Inner class for equipment statistics
     */
    public static class EquipmentStats {
        private final int totalEquipment;
        private final int availableEquipment;
        private final int unusedEquipment;
        private final Map<String, Integer> typeStats;
        private final String mostRequested;

        public EquipmentStats(int totalEquipment, int availableEquipment, int unusedEquipment,
                              Map<String, Integer> typeStats, String mostRequested) {
            this.totalEquipment = totalEquipment;
            this.availableEquipment = availableEquipment;
            this.unusedEquipment = unusedEquipment;
            this.typeStats = typeStats;
            this.mostRequested = mostRequested;
        }

        public int getTotalEquipment() { return totalEquipment; }
        public int getAvailableEquipment() { return availableEquipment; }
        public int getUnusedEquipment() { return unusedEquipment; }
        public Map<String, Integer> getTypeStats() { return typeStats; }
        public String getMostRequested() { return mostRequested; }
        public double getUsagePercentage() {
            return totalEquipment > 0 ? ((double) (totalEquipment - unusedEquipment) / totalEquipment) * 100 : 0.0;
        }
    }
}