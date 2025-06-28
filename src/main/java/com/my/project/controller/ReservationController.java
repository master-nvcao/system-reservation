package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Salle;
import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class ReservationController {

    @FXML private ComboBox<Salle> salleComboBox;
    @FXML private DatePicker datePicker;
    @FXML private TextField heureDebutField;
    @FXML private TextField heureFinField;
    @FXML private TextArea descriptionArea;
    @FXML private Label messageLabel;

    private Utilisateur utilisateurConnecte;

    @FXML
    public void initialize() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Salle> salles = session.createQuery("FROM Salle WHERE disponible = true", Salle.class).list();
            salleComboBox.getItems().addAll(salles);
        }
    }

    @FXML
    private void handleReservation() {
        Salle salle = salleComboBox.getValue();
        LocalDate date = datePicker.getValue();
        String heureDebutStr = heureDebutField.getText();
        String heureFinStr = heureFinField.getText();
        String desc = descriptionArea.getText();

        if (salle == null || date == null || heureDebutStr.isEmpty() || heureFinStr.isEmpty()) {
            messageLabel.setText("Tous les champs sont obligatoires.");
            return;
        }

        try {
            LocalTime debut = LocalTime.parse(heureDebutStr);
            LocalTime fin = LocalTime.parse(heureFinStr);
            LocalDateTime dateDebut = LocalDateTime.of(date, debut);
            LocalDateTime dateFin = LocalDateTime.of(date, fin);

            if (!verifierReglesMetier(dateDebut, dateFin)) {
                return;
            }

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                // Vérification disponibilité de la salle
                List<Reservation> conflitsSalle = session.createQuery(
                                "FROM Reservation WHERE salle = :salle AND " +
                                        "((:debut BETWEEN dateDebut AND dateFin) OR " +
                                        "(:fin BETWEEN dateDebut AND dateFin) OR " +
                                        "(:debut < dateDebut AND :fin > dateFin))", Reservation.class)
                        .setParameter("salle", salle)
                        .setParameter("debut", dateDebut)
                        .setParameter("fin", dateFin)
                        .list();

                if (!conflitsSalle.isEmpty()) {
                    messageLabel.setText("Créneau déjà réservé pour cette salle.");
                    return;
                }

                // OK : enregistrement
                Transaction tx = session.beginTransaction();
                Reservation res = new Reservation();
                res.setSalle(salle);
                res.setUtilisateur(utilisateurConnecte);
                res.setDateDebut(dateDebut);
                res.setDateFin(dateFin);
                res.setDescription(desc);

                session.persist(res);
                tx.commit();

                messageLabel.setStyle("-fx-text-fill: green;");
                messageLabel.setText("Réservation enregistrée !");
            }
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("Erreur lors de la réservation.");
        }
    }

    private boolean verifierReglesMetier(LocalDateTime debut, LocalDateTime fin) {
        if (debut.isBefore(LocalDateTime.now())) {
            messageLabel.setText("Impossible de réserver dans le passé.");
            return false;
        }

        if (!fin.isAfter(debut)) {
            messageLabel.setText("L'heure de fin doit être après l'heure de début.");
            return false;
        }

        long duree = Duration.between(debut, fin).toHours();
        if (duree > 4) {
            messageLabel.setText("Durée maximale autorisée : 4 heures.");
            return false;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // 1. Max 3 réservations / jour
            LocalDate jour = debut.toLocalDate();
            LocalDateTime debutJour = jour.atStartOfDay();
            LocalDateTime finJour = jour.plusDays(1).atStartOfDay();

            long nbResaJour = session.createQuery(
                            "SELECT COUNT(*) FROM Reservation WHERE utilisateur = :u AND dateDebut BETWEEN :d1 AND :d2", Long.class)
                    .setParameter("u", utilisateurConnecte)
                    .setParameter("d1", debutJour)
                    .setParameter("d2", finJour)
                    .uniqueResult();

            if (nbResaJour >= 3) {
                messageLabel.setText("Limite de 3 réservations par jour atteinte.");
                return false;
            }

            // 2. Aucun chevauchement autorisé (même utilisateur)
            List<Reservation> chevauchements = session.createQuery(
                            "FROM Reservation WHERE utilisateur = :u AND " +
                                    "((:start BETWEEN dateDebut AND dateFin) OR " +
                                    "(:end BETWEEN dateDebut AND dateFin) OR " +
                                    "(:start < dateDebut AND :end > dateFin))", Reservation.class)
                    .setParameter("u", utilisateurConnecte)
                    .setParameter("start", debut)
                    .setParameter("end", fin)
                    .list();

            if (!chevauchements.isEmpty()) {
                messageLabel.setText("Vous avez déjà une réservation à ce moment-là.");
                return false;
            }
        }

        return true;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateurConnecte = utilisateur;
    }
}
