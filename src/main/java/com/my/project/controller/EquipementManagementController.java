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

import java.util.*;
import java.util.stream.Collectors;

public class EquipementManagementController {

    @FXML private TableView<Equipement> equipementTable;
    @FXML private TableColumn<Equipement, Long> idCol;
    @FXML private TableColumn<Equipement, String> nomCol;
    @FXML private TableColumn<Equipement, String> descCol;
    @FXML private TableColumn<Equipement, String> typeCol;
    @FXML private TableColumn<Equipement, String> sallesCol;

    @FXML
    public void initialize() {
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getId()).asObject());
        nomCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getNom()));
        descCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));
        typeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType()));
        sallesCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                getNomsSalles(data.getValue())
        ));

        chargerEquipements();
    }

    private String getNomsSalles(Equipement e) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Equipement attached = session.get(Equipement.class, e.getId());
            List<String> noms = attached == null ? List.of() :
                    session.createQuery("FROM Salle s WHERE :eq MEMBER OF s.equipements", Salle.class)
                            .setParameter("eq", attached)
                            .list()
                            .stream()
                            .map(Salle::getNom)
                            .toList();
            return String.join(", ", noms);
        }
    }

    private void chargerEquipements() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Equipement> liste = session.createQuery("FROM Equipement", Equipement.class).list();
            equipementTable.setItems(FXCollections.observableArrayList(liste));
        }
    }

    @FXML
    private void handleAdd() {
        Dialog<Equipement> dialog = new Dialog<>();
        dialog.setTitle("Ajouter un équipement");
        dialog.setHeaderText("Remplissez les informations de l'équipement");

        ButtonType ajouterBtn = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ajouterBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField nomField = new TextField();
        TextField descField = new TextField();
        TextField typeField = new TextField();

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        grid.add(new Label("Type:"), 0, 2); grid.add(typeField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == ajouterBtn) {
                Equipement e = new Equipement();
                e.setNom(nomField.getText());
                e.setDescription(descField.getText());
                e.setType(typeField.getText());
                return e;
            }
            return null;
        });

        Optional<Equipement> result = dialog.showAndWait();
        result.ifPresent(eq -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.persist(eq);
                tx.commit();
                chargerEquipements();
            }
        });
    }

    @FXML
    private void handleEdit() {
        Equipement eq = equipementTable.getSelectionModel().getSelectedItem();
        if (eq == null) {
            alert("Sélection requise", "Veuillez sélectionner un équipement à modifier.");
            return;
        }

        Dialog<Equipement> dialog = new Dialog<>();
        dialog.setTitle("Modifier un équipement");
        dialog.setHeaderText("Modifiez les champs souhaités");

        ButtonType modifierBtn = new ButtonType("Mettre à jour", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(modifierBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField nomField = new TextField(eq.getNom());
        TextField descField = new TextField(eq.getDescription());
        TextField typeField = new TextField(eq.getType());

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        grid.add(new Label("Type:"), 0, 2); grid.add(typeField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == modifierBtn) {
                eq.setNom(nomField.getText());
                eq.setDescription(descField.getText());
                eq.setType(typeField.getText());
                return eq;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updatedEq -> {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.merge(updatedEq);
                tx.commit();
                chargerEquipements();
            }
        });
    }


    @FXML
    private void handleDelete() {
        Equipement eq = equipementTable.getSelectionModel().getSelectedItem();
        if (eq == null) {
            alert("Sélection requise", "Veuillez sélectionner un équipement.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Equipement attached = session.merge(eq);

            // Détacher cet équipement de toutes les salles
            List<Salle> salles = session.createQuery("FROM Salle s WHERE :eq MEMBER OF s.equipements", Salle.class)
                    .setParameter("eq", attached).list();

            for (Salle s : salles) {
                s.getEquipements().remove(attached);
                session.merge(s);
            }

            session.remove(attached);
            tx.commit();
            chargerEquipements();
        }
    }

    @FXML
    private void handleAssocierSalles() {
        Equipement eq = equipementTable.getSelectionModel().getSelectedItem();
        if (eq == null) {
            alert("Sélection requise", "Veuillez sélectionner un équipement.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Salle> toutes = session.createQuery("FROM Salle", Salle.class).list();
            ListView<String> listView = new ListView<>();
            listView.getItems().addAll(toutes.stream().map(Salle::getNom).toList());
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            // Pré-sélectionner les salles déjà associées
            List<Salle> associees = session.createQuery(
                            "FROM Salle s WHERE :eq MEMBER OF s.equipements", Salle.class)
                    .setParameter("eq", eq)
                    .list();

            for (Salle salle : associees) {
                listView.getSelectionModel().select(salle.getNom());
            }

            Dialog<List<String>> dialog = new Dialog<>();
            dialog.setTitle("Associer à des salles");
            dialog.getDialogPane().setContent(listView);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    return new ArrayList<>(listView.getSelectionModel().getSelectedItems());
                }
                return null;
            });

            Optional<List<String>> result = dialog.showAndWait();
            if (result.isPresent()) {
                List<String> selection = result.get();
                Transaction tx = session.beginTransaction();
                Equipement attachedEq = session.merge(eq);

                List<Salle> update = session.createQuery("FROM Salle", Salle.class).list();
                for (Salle s : update) {
                    if (selection.contains(s.getNom())) {
                        s.getEquipements().add(attachedEq);
                    } else {
                        s.getEquipements().remove(attachedEq);
                    }
                    session.merge(s);
                }
                tx.commit();
                chargerEquipements();
            }
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) equipementTable.getScene().getWindow();
        stage.close();
    }

    private void alert(String titre, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
