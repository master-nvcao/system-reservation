package com.my.project.service;

import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

public class UserService extends BaseService<Utilisateur> {

    public UserService() {
        super(Utilisateur.class);
    }

    /**
     * Get count of all active users
     */
    public int getActiveUsersCount() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(u) FROM Utilisateur u", Long.class);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting active users count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get count of users by role
     */
    public int getUserCountByRole(String role) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(u) FROM Utilisateur u WHERE u.role = :role", Long.class);
            query.setParameter("role", role);
            return Math.toIntExact(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error getting user count by role: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get all users by role
     */
    public List<Utilisateur> getUsersByRole(String role) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Utilisateur> query = session.createQuery(
                    "FROM Utilisateur u WHERE u.role = :role", Utilisateur.class);
            query.setParameter("role", role);
            return query.list();
        } catch (Exception e) {
            System.err.println("Error getting users by role: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Find user by email
     */
    public Optional<Utilisateur> findByEmail(String email) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Utilisateur> query = session.createQuery(
                    "FROM Utilisateur u WHERE u.email = :email", Utilisateur.class);
            query.setParameter("email", email);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error finding user by email: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Authenticate user
     */
    public Optional<Utilisateur> authenticate(String email, String password) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Utilisateur> query = session.createQuery(
                    "FROM Utilisateur u WHERE u.email = :email AND u.motDePasse = :password",
                    Utilisateur.class);
            query.setParameter("email", email);
            query.setParameter("password", password);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            System.err.println("Error authenticating user: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get user statistics
     */
    public UserStats getUserStats() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            int totalUsers = Math.toIntExact(count());
            int admins = getUserCountByRole("admin");
            int professors = getUserCountByRole("professeur");
            int students = getUserCountByRole("etudiant");

            return new UserStats(totalUsers, admins, professors, students);
        } catch (Exception e) {
            System.err.println("Error getting user statistics: " + e.getMessage());
            return new UserStats(0, 0, 0, 0);
        }
    }

    /**
     * Inner class for user statistics
     */
    public static class UserStats {
        private final int totalUsers;
        private final int admins;
        private final int professors;
        private final int students;

        public UserStats(int totalUsers, int admins, int professors, int students) {
            this.totalUsers = totalUsers;
            this.admins = admins;
            this.professors = professors;
            this.students = students;
        }

        public int getTotalUsers() { return totalUsers; }
        public int getAdmins() { return admins; }
        public int getProfessors() { return professors; }
        public int getStudents() { return students; }
    }
}