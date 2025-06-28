package com.my.project.controller;

import com.my.project.model.Equipement;
import com.my.project.model.Reservation;
import com.my.project.model.Salle;
import com.my.project.util.HibernateUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.hibernate.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class SuggestionController {

    @FXML private DatePicker datePicker;
    @FXML private TextField heureDebutField;
    @FXML private TextField heureFinField;
    @FXML private TextField capaciteField;
    @FXML private ListView<String> equipementList;
    @FXML private TextArea resultArea;

    @FXML
    public void initialize() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Equipement> eqList = session.createQuery("FROM Equipement", Equipement.class).list();
            equipementList.setItems(FXCollections.observableArrayList(
                    eqList.stream().map(Equipement::getNom).toList()));
            equipementList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        }
    }

    @FXML
    private void handleSuggestion() {
        resultArea.clear();

        LocalDate date = datePicker.getValue();
        String hd = heureDebutField.getText();
        String hf = heureFinField.getText();
        int capacite;

        if (date == null || hd.isBlank() || hf.isBlank() || capaciteField.getText().isBlank()) {
            resultArea.setText("Tous les champs sont obligatoires.");
            return;
        }

        try {
            LocalTime timeDebut = LocalTime.parse(hd);
            LocalTime timeFin = LocalTime.parse(hf);
            LocalDateTime debut = LocalDateTime.of(date, timeDebut);
            LocalDateTime fin = LocalDateTime.of(date, timeFin);
            capacite = Integer.parseInt(capaciteField.getText());
            List<String> equipementsSouhaites = equipementList.getSelectionModel().getSelectedItems();

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                List<Salle> salles = session.createQuery("FROM Salle WHERE disponible = true AND capacite >= :cap", Salle.class)
                        .setParameter("cap", capacite)
                        .list();

                List<Salle> filtrées = new ArrayList<>();
                for (Salle s : salles) {
                    boolean okEquip = equipementsSouhaites.stream()
                            .allMatch(eq -> s.getEquipements().stream().map(Equipement::getNom).toList().contains(eq));

                    boolean dispo = session.createQuery(
                                    "FROM Reservation WHERE salle = :salle AND " +
                                            "((:start BETWEEN dateDebut AND dateFin) OR " +
                                            "(:end BETWEEN dateDebut AND dateFin) OR " +
                                            "(:start < dateDebut AND :end > dateFin))", Reservation.class)
                            .setParameter("salle", s)
                            .setParameter("start", debut)
                            .setParameter("end", fin)
                            .list().isEmpty();

                    if (okEquip && dispo) filtrées.add(s);
                }

                if (!filtrées.isEmpty()) {
                    resultArea.setText("Salles disponibles :\n" + filtrées.stream().map(Salle::getNom).collect(Collectors.joining("\n")));
                } else {
                    resultArea.setText("Aucune salle disponible pour ce créneau.\nSuggestions alternatives :\n");
                    for (Salle s : salles) {
                        boolean okEquip = equipementsSouhaites.stream()
                                .allMatch(eq -> s.getEquipements().stream().map(Equipement::getNom).toList().contains(eq));
                        if (!okEquip) continue;

                        for (int h = 8; h <= 18; h++) {
                            LocalDateTime altDeb = date.atTime(h, 0);
                            LocalDateTime altFin = altDeb.plusHours(2);

                            boolean libre = session.createQuery(
                                            "FROM Reservation WHERE salle = :salle AND " +
                                                    "((:start BETWEEN dateDebut AND dateFin) OR " +
                                                    "(:end BETWEEN dateDebut AND dateFin) OR " +
                                                    "(:start < dateDebut AND :end > dateFin))", Reservation.class)
                                    .setParameter("salle", s)
                                    .setParameter("start", altDeb)
                                    .setParameter("end", altFin)
                                    .list().isEmpty();

                            if (libre) {
                                resultArea.appendText("Salle : " + s.getNom() + " - de " + altDeb.toLocalTime() + " à " + altFin.toLocalTime() + "\n");
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            resultArea.setText("Erreur : vérifiez les champs (format heure hh:mm, capacité numérique).");
            e.printStackTrace();
        }
    }
}
