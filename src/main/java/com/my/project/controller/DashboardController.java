package com.my.project.controller;

import com.my.project.model.Reservation;
import com.my.project.model.Utilisateur;
import com.my.project.util.HibernateUtil;
import org.hibernate.Session;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    @FXML private Label welcomeLabel;

    private Utilisateur utilisateurConnecte;

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateurConnecte = utilisateur;
        welcomeLabel.setText("Bienvenue, " + utilisateur.getNom() + " !");
        lancerNotifications(utilisateur); // ← démarre la vérification des rappels
    }

    @FXML
    private void handleOpenReservation() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/reservation.fxml"));
            Scene scene = new Scene(loader.load());

            ReservationController controller = loader.getController();
            controller.setUtilisateur(utilisateurConnecte);

            Stage stage = new Stage();
            stage.setTitle("Nouvelle réservation");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleOpenMyReservations() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/my_reservations.fxml"));
            Scene scene = new Scene(loader.load());

            MyReservationsController controller = loader.getController();
            controller.setUtilisateur(utilisateurConnecte);

            Stage stage = new Stage();
            stage.setTitle("Mes Réservations");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleOpenSuggestion() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/suggestion.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Suggestion intelligente");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @FXML
    private void handleLogout() {
        // Fermer le dashboard et revenir à la page de login
        Stage currentStage = (Stage) welcomeLabel.getScene().getWindow();
        currentStage.close();
    }

    private void lancerNotifications(Utilisateur user) {
        Thread thread = new Thread(() -> {
            while (true) {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Reservation> proches = session.createQuery(
                                    "FROM Reservation WHERE utilisateur = :u AND dateDebut BETWEEN :now AND :in10",
                                    Reservation.class)
                            .setParameter("u", user)
                            .setParameter("now", java.time.LocalDateTime.now())
                            .setParameter("in10", java.time.LocalDateTime.now().plusMinutes(10))
                            .list();

                    if (!proches.isEmpty()) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Rappel de réservation");
                            alert.setHeaderText("⚠️ Vous avez une réservation bientôt !");
                            alert.setContentText("Début : " + proches.get(0).getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) +
                                    "\nSalle : " + proches.get(0).getSalle().getNom());
                            alert.show();
                        });
                    }

                    Thread.sleep(5 * 60 * 1000); // vérifie toutes les 5 minutes
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        thread.setDaemon(true); // important pour ne pas bloquer la fermeture
        thread.start();
    }


}
