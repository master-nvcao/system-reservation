package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Salle;
import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
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
        setupComboBox();
        loadSalles();
    }

    private void setupComboBox() {
        // Configure ComboBox to display salle names properly
        salleComboBox.setCellFactory(listView -> new ListCell<Salle>() {
            @Override
            protected void updateItem(Salle salle, boolean empty) {
                super.updateItem(salle, empty);
                if (empty || salle == null) {
                    setText(null);
                } else {
                    setText(salle.getNom() + " (Capacité: " + salle.getCapacite() + ")");
                }
            }
        });

        salleComboBox.setButtonCell(new ListCell<Salle>() {
            @Override
            protected void updateItem(Salle salle, boolean empty) {
                super.updateItem(salle, empty);
                if (empty || salle == null) {
                    setText("Sélectionner une salle");
                } else {
                    setText(salle.getNom());
                }
            }
        });
    }

    private void loadSalles() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Salle> salles = session.createQuery("FROM Salle WHERE disponible = true ORDER BY nom", Salle.class).list();
            salleComboBox.getItems().clear();
            salleComboBox.getItems().addAll(salles);
        } catch (Exception e) {
            showErrorMessage("Erreur lors du chargement des salles.");
        }
    }

    @FXML
    private void handleReservation() {
        if (!validateForm()) {
            return;
        }

        Salle salle = salleComboBox.getValue();
        LocalDate date = datePicker.getValue();
        String heureDebutStr = heureDebutField.getText().trim();
        String heureFinStr = heureFinField.getText().trim();
        String description = descriptionArea.getText().trim();

        try {
            LocalTime debut = LocalTime.parse(heureDebutStr);
            LocalTime fin = LocalTime.parse(heureFinStr);
            LocalDateTime dateDebut = LocalDateTime.of(date, debut);
            LocalDateTime dateFin = LocalDateTime.of(date, fin);

            if (!verifierReglesMetier(dateDebut, dateFin)) {
                return;
            }

            // Check for conflicts with APPROVED reservations only
            if (!verifierDisponibiliteSalle(salle, dateDebut, dateFin)) {
                return;
            }

            // Create reservation request (status: EN_ATTENTE)
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();

                Reservation reservation = new Reservation();
                reservation.setSalle(salle);
                reservation.setUtilisateur(utilisateurConnecte);
                reservation.setDateDebut(dateDebut);
                reservation.setDateFin(dateFin);
                reservation.setDescription(description);
                reservation.setStatut(Reservation.StatutReservation.EN_ATTENTE);
                reservation.setDateCreation(LocalDateTime.now());

                session.persist(reservation);
                tx.commit();

                showSuccessMessage("Demande de réservation envoyée ! Elle sera examinée par un administrateur.");
                clearForm();

            } catch (Exception e) {
                e.printStackTrace();
                showErrorMessage("Erreur lors de l'envoi de la demande.");
            }

        } catch (Exception e) {
            showErrorMessage("Format d'heure invalide. Utilisez HH:MM (ex: 14:30)");
        }
    }

    private boolean validateForm() {
        if (utilisateurConnecte == null) {
            showErrorMessage("Aucun utilisateur connecté.");
            return false;
        }

        if (salleComboBox.getValue() == null) {
            showErrorMessage("Veuillez sélectionner une salle.");
            return false;
        }

        if (datePicker.getValue() == null) {
            showErrorMessage("Veuillez sélectionner une date.");
            return false;
        }

        if (heureDebutField.getText().trim().isEmpty()) {
            showErrorMessage("Veuillez saisir l'heure de début.");
            return false;
        }

        if (heureFinField.getText().trim().isEmpty()) {
            showErrorMessage("Veuillez saisir l'heure de fin.");
            return false;
        }

        if (descriptionArea.getText().trim().isEmpty()) {
            showErrorMessage("Veuillez saisir une description.");
            return false;
        }

        return true;
    }

    private boolean verifierReglesMetier(LocalDateTime debut, LocalDateTime fin) {
        // Check if reservation is not in the past
        if (debut.isBefore(LocalDateTime.now())) {
            showErrorMessage("Impossible de réserver dans le passé.");
            return false;
        }

        // Check if end time is after start time
        if (!fin.isAfter(debut)) {
            showErrorMessage("L'heure de fin doit être après l'heure de début.");
            return false;
        }

        // Check duration limit (4 hours max)
        long dureeMinutes = Duration.between(debut, fin).toMinutes();
        if (dureeMinutes > 240) { // 4 hours = 240 minutes
            showErrorMessage("Durée maximale autorisée : 4 heures.");
            return false;
        }

        // Check minimum duration (15 minutes)
        if (dureeMinutes < 15) {
            showErrorMessage("Durée minimale requise : 15 minutes.");
            return false;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Check daily request limit (5 requests per day including pending)
            LocalDate jour = debut.toLocalDate();
            LocalDateTime debutJour = jour.atStartOfDay();
            LocalDateTime finJour = jour.plusDays(1).atStartOfDay();

            long nbDemandesJour = session.createQuery(
                            "SELECT COUNT(*) FROM Reservation WHERE utilisateur = :u AND dateDebut >= :d1 AND dateDebut < :d2 AND statut != :statut", Long.class)
                    .setParameter("u", utilisateurConnecte)
                    .setParameter("d1", debutJour)
                    .setParameter("d2", finJour)
                    .setParameter("statut", Reservation.StatutReservation.REJETEE)
                    .uniqueResult();

            if (nbDemandesJour >= 5) {
                showErrorMessage("Limite de 5 demandes par jour atteinte (en attente + approuvées).");
                return false;
            }

            // Check for user conflicts with APPROVED reservations only
            List<Reservation> conflitsUtilisateur = session.createQuery(
                            "FROM Reservation WHERE utilisateur = :u AND statut = :statut AND " +
                                    "((:debut < dateFin AND :fin > dateDebut))", Reservation.class)
                    .setParameter("u", utilisateurConnecte)
                    .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                    .setParameter("debut", debut)
                    .setParameter("fin", fin)
                    .list();

            if (!conflitsUtilisateur.isEmpty()) {
                showErrorMessage("Vous avez déjà une réservation approuvée qui chevauche avec ce créneau.");
                return false;
            }
        } catch (Exception e) {
            showErrorMessage("Erreur lors de la vérification des règles métier.");
            return false;
        }

        return true;
    }

    private boolean verifierDisponibiliteSalle(Salle salle, LocalDateTime debut, LocalDateTime fin) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Check conflicts with APPROVED reservations only
            List<Reservation> conflitsSalle = session.createQuery(
                            "FROM Reservation WHERE salle = :salle AND statut = :statut AND " +
                                    "((:debut < dateFin AND :fin > dateDebut))", Reservation.class)
                    .setParameter("salle", salle)
                    .setParameter("statut", Reservation.StatutReservation.APPROUVEE)
                    .setParameter("debut", debut)
                    .setParameter("fin", fin)
                    .list();

            if (!conflitsSalle.isEmpty()) {
                showErrorMessage("Cette salle est déjà réservée (approuvée) pour ce créneau.");
                return false;
            }

            return true;
        } catch (Exception e) {
            showErrorMessage("Erreur lors de la vérification de disponibilité.");
            return false;
        }
    }

    @FXML
    private void handleCancel() {
        clearForm();
        // Optionally close the window
        Stage stage = (Stage) salleComboBox.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void clearForm() {
        salleComboBox.setValue(null);
        datePicker.setValue(null);
        heureDebutField.clear();
        heureFinField.clear();
        descriptionArea.clear();
        messageLabel.setText("");
    }

    private void showErrorMessage(String message) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
    }

    private void showSuccessMessage(String message) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateurConnecte = utilisateur;
        System.out.println("Utilisateur connecté: " + (utilisateur != null ? utilisateur.getNom() : "null"));
    }

    public Utilisateur getUtilisateur() {
        return utilisateurConnecte;
    }
}