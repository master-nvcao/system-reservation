package com.my.project;

import com.my.project.model.Equipement;
import com.my.project.model.Salle;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.Arrays;

public class TestSalleEquipement {

    public static void main(String[] args) {
        // Création d’équipements
        Equipement projecteur = new Equipement();
        projecteur.setNom("Projecteur");
        projecteur.setDescription("Projecteur HD");
        projecteur.setType("vidéo");

        Equipement tableau = new Equipement();
        tableau.setNom("Tableau blanc");
        tableau.setDescription("Tableau magnétique");
        tableau.setType("écriture");

        // Création d'une salle
        Salle salle = new Salle();
        salle.setNom("Salle A1");
        salle.setCapacite(30);
        salle.setType("cours");
        salle.setDisponible(true);
        salle.setEquipements(Arrays.asList(projecteur, tableau));

        // Insertion via Hibernate
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            // Hibernate persiste automatiquement les équipements liés si cascade (sinon enregistrer d'abord)
            session.persist(projecteur);
            session.persist(tableau);
            session.persist(salle);

            tx.commit();
            System.out.println("Salle et équipements enregistrés !");
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        } finally {
            HibernateUtil.shutdown();
        }
    }
}
