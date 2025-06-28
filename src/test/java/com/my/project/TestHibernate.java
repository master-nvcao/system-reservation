package com.my.project;

import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class TestHibernate {

    public static void main(String[] args) {
        // Créer un nouvel utilisateur
        Utilisateur user = new Utilisateur();
        user.setNom("Jean Dupont");
        user.setEmail("jean.dupont@example.com");
        user.setMotDePasse("motdepasse123");
        user.setRole("etudiant");

        // Insérer en base via Hibernate
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            session.persist(user);

            transaction.commit();
            System.out.println("Utilisateur inséré avec succès !");
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        } finally {
            HibernateUtil.shutdown();
        }
    }
}
