package com.my.project.controller;

import com.my.project.model.Equipement;
import com.my.project.model.Salle;
import com.my.project.util.HibernateUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RoomManagementController {

    @FXML private TableView<Salle> salleTable;
    @FXML private TableColumn<Salle, Long> idCol;
    @FXML private TableColumn<Salle, String> nomCol;
    @FXML private TableColumn<Salle, Integer> capaciteCol;
    @FXML private TableColumn<Salle, String> typeCol;
    @FXML private TableColumn<Salle, String> dispoCol;
    @FXML private TableColumn<Salle, String> equipementCol;

    @FXML
    public void initialize() {
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getId()).asObject());
        nomCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getNom()));
        capaciteCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getCapacite()).asObject());
        typeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType()));
        dispoCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().isDisponible() ? "Oui" : "Non"));
        equipementCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getEquipements().stream()
                        .map(Equipement::getNom)
                        .collect(Collectors.joining(", "))
        ));

        chargerSalles();
    }

    private void chargerSalles() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Salle> salles = session.createQuery(
                    "SELECT DISTINCT s FROM Salle s LEFT JOIN FETCH s.equipements", Salle.class
            ).list();
            salleTable.setItems(FXCollections.observableArrayList(salles));
        }
    }

    @FXML
    private void handleAdd() {
        Dialog<Salle> dialog = new Dialog<>();
        dialog.setTitle("Ajouter une salle");
        dialog.setHeaderText("Entrez les informations de la nouvelle salle");

        ButtonType ajouterButton = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ajouterButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField nomField = new TextField();
        TextField capaciteField = new TextField();
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("cours", "td", "amphi"));
        typeBox.getSelectionModel().selectFirst();
        CheckBox dispoBox = new CheckBox("Disponible");

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Capacité:"), 0, 1); grid.add(capaciteField, 1, 1);
        grid.add(new Label("Type:"), 0, 2); grid.add(typeBox, 1, 2);
        grid.add(new Label("Disponibilité:"), 0, 3); grid.add(dispoBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ajouterButton) {
                try {
                    Salle s = new Salle();
                    s.setNom(nomField.getText());
                    s.setCapacite(Integer.parseInt(capaciteField.getText()));
                    s.setType(typeBox.getValue());
                    s.setDisponible(dispoBox.isSelected());
                    s.setEquipements(new ArrayList<>());
                    return s;
                } catch (NumberFormatException e) {
                    alert("Erreur de saisie", "La capacité doit être un nombre entier.");
                    return null;
                }
            }
            return null;
        });

        Optional<Salle> result = dialog.showAndWait();
        result.ifPresent(salle -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.persist(salle);
                tx.commit();
                chargerSalles();
            }
        });
    }

    @FXML
    private void handleEdit() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            alert("Sélection requise", "Veuillez sélectionner une salle à modifier.");
            return;
        }

        Dialog<Salle> dialog = new Dialog<>();
        dialog.setTitle("Modifier une salle");
        dialog.setHeaderText("Modifiez les informations de la salle");

        ButtonType updateButton = new ButtonType("Mettre à jour", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField nomField = new TextField(salle.getNom());
        TextField capaciteField = new TextField(String.valueOf(salle.getCapacite()));
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("cours", "td", "amphi"));
        typeBox.setValue(salle.getType());
        CheckBox dispoBox = new CheckBox("Disponible");
        dispoBox.setSelected(salle.isDisponible());

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Capacité:"), 0, 1); grid.add(capaciteField, 1, 1);
        grid.add(new Label("Type:"), 0, 2); grid.add(typeBox, 1, 2);
        grid.add(new Label("Disponibilité:"), 0, 3); grid.add(dispoBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == updateButton) {
                try {
                    salle.setNom(nomField.getText());
                    salle.setCapacite(Integer.parseInt(capaciteField.getText()));
                    salle.setType(typeBox.getValue());
                    salle.setDisponible(dispoBox.isSelected());
                    return salle;
                } catch (NumberFormatException e) {
                    alert("Erreur de saisie", "La capacité doit être un nombre entier.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updatedSalle -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.merge(updatedSalle);
                tx.commit();
                chargerSalles();
            }
        });
    }


    @FXML
    private void handleDelete() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            alert("Sélection requise", "Veuillez sélectionner une salle à supprimer.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Salle attached = session.merge(salle);
            attached.getEquipements().clear(); // dissocier les équipements
            session.remove(attached);
            tx.commit();
            chargerSalles();
        } catch (Exception e) {
            alert("Erreur", "Impossible de supprimer la salle.");
        }
    }

    @FXML
    private void handleToggleDisponibilite() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            alert("Sélection requise", "Veuillez sélectionner une salle.");
            return;
        }

        salle.setDisponible(!salle.isDisponible());
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(salle);
            tx.commit();
            chargerSalles();
        }
    }

    @FXML
    private void handleAssocierEquipements() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            alert("Sélection requise", "Veuillez sélectionner une salle.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Equipement> tous = session.createQuery("FROM Equipement", Equipement.class).list();

            List<String> noms = tous.stream().map(Equipement::getNom).toList();
            ListView<String> listView = new ListView<>(FXCollections.observableArrayList(noms));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            for (Equipement e : salle.getEquipements()) {
                listView.getSelectionModel().select(e.getNom());
            }

            Dialog<List<String>> dialog = new Dialog<>();
            dialog.setTitle("Associer des équipements");
            dialog.getDialogPane().setContent(listView);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    return new ArrayList<>(listView.getSelectionModel().getSelectedItems());
                }
                return null;
            });

            Optional<List<String>> result = dialog.showAndWait();
            if (result.isPresent()) {
                List<Equipement> selectionnes = tous.stream()
                        .filter(eq -> result.get().contains(eq.getNom()))
                        .toList();

                salle.setEquipements(new ArrayList<>(selectionnes));
                Transaction tx = session.beginTransaction();
                session.merge(salle);
                tx.commit();
                chargerSalles();
            }
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) salleTable.getScene().getWindow();
        stage.close();
    }

    private void alert(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
