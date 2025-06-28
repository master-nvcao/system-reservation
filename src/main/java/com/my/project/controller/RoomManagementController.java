package com.my.project.controller;

import com.my.project.model.Equipement;
import com.my.project.model.Salle;
import com.my.project.util.HibernateUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class RoomManagementController implements Initializable {

    @FXML private TableView<Salle> salleTable;
    @FXML private TableColumn<Salle, Long> idCol;
    @FXML private TableColumn<Salle, String> nomCol;
    @FXML private TableColumn<Salle, Integer> capaciteCol;
    @FXML private TableColumn<Salle, String> typeCol;
    @FXML private TableColumn<Salle, String> dispoCol;
    @FXML private TableColumn<Salle, String> equipementCol;

    // Statistics labels
    @FXML private Label totalSallesCount;
    @FXML private Label sallesDisponiblesCount;
    @FXML private Label sallesOccupeesCount;
    @FXML private Label capaciteTotaleCount;
    @FXML private Label selectionLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupTableSelection();
        chargerSalles();
    }

    private void setupTableColumns() {
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getId()).asObject());
        nomCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getNom()));
        capaciteCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getCapacite()).asObject());
        typeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType().toUpperCase()));

        // Custom cell factory for availability column
        dispoCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().isDisponible() ? "true" : "false"));
        dispoCol.setCellFactory(column -> new TableCell<Salle, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    if ("true".equals(item)) {
                        setText("true");
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    } else {
                        setText("false");
                        setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                    }
                }
            }
        });

        equipementCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getEquipements().stream()
                        .map(Equipement::getNom)
                        .collect(Collectors.joining(", "))
        ));
    }

    private void setupTableSelection() {
        salleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectionLabel.setText("Sélection: " + newSelection.getNom() + " (ID: " + newSelection.getId() + ")");
                selectionLabel.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: 600;");
            } else {
                selectionLabel.setText("Aucune sélection");
                selectionLabel.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 500;");
            }
        });

        // Add row factory for custom styling
        salleTable.setRowFactory(tv -> {
            TableRow<Salle> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldSalle, newSalle) -> {
                if (newSalle == null) {
                    row.setStyle("");
                } else if (!newSalle.isDisponible()) {
                    row.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #fecaca; -fx-border-width: 0 0 0 3;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    private void chargerSalles() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Salle> salles = session.createQuery(
                    "SELECT DISTINCT s FROM Salle s LEFT JOIN FETCH s.equipements", Salle.class
            ).list();
            salleTable.setItems(FXCollections.observableArrayList(salles));
            updateStatistics(salles);
        }
    }

    private void updateStatistics(List<Salle> salles) {
        if (totalSallesCount != null) {
            totalSallesCount.setText(String.valueOf(salles.size()));
        }

        long disponibles = salles.stream().filter(Salle::isDisponible).count();
        if (sallesDisponiblesCount != null) {
            sallesDisponiblesCount.setText(String.valueOf(disponibles));
        }

        if (sallesOccupeesCount != null) {
            sallesOccupeesCount.setText(String.valueOf(salles.size() - disponibles));
        }

        int capaciteTotale = salles.stream().mapToInt(Salle::getCapacite).sum();
        if (capaciteTotaleCount != null) {
            capaciteTotaleCount.setText(String.valueOf(capaciteTotale));
        }
    }

    @FXML
    private void handleAdd() {
        Dialog<Salle> dialog = createSalleDialog("Ajouter une salle", "Entrez les informations de la nouvelle salle", null);

        Optional<Salle> result = dialog.showAndWait();
        result.ifPresent(salle -> {
            if (salle != null) {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    Transaction tx = session.beginTransaction();
                    session.persist(salle);
                    tx.commit();
                    chargerSalles();
                    showSuccessAlert("Succès", "Salle ajoutée avec succès !");
                } catch (Exception e) {
                    showErrorAlert("Erreur", "Impossible d'ajouter la salle: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleEdit() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            showWarningAlert("Sélection requise", "Veuillez sélectionner une salle à modifier.");
            return;
        }

        Dialog<Salle> dialog = createSalleDialog("Modifier une salle", "Modifiez les informations de la salle", salle);

        dialog.showAndWait().ifPresent(updatedSalle -> {
            if (updatedSalle != null) {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    Transaction tx = session.beginTransaction();
                    session.merge(updatedSalle);
                    tx.commit();
                    chargerSalles();
                    showSuccessAlert("Succès", "Salle modifiée avec succès !");
                } catch (Exception e) {
                    showErrorAlert("Erreur", "Impossible de modifier la salle: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleDelete() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            showWarningAlert("Sélection requise", "Veuillez sélectionner une salle à supprimer.");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmer la suppression");
        confirmAlert.setHeaderText("Supprimer la salle");
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer la salle \"" + salle.getNom() + "\" ?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                Salle attached = session.merge(salle);
                attached.getEquipements().clear(); // dissocier les équipements
                session.remove(attached);
                tx.commit();
                chargerSalles();
                showSuccessAlert("Succès", "Salle supprimée avec succès !");
            } catch (Exception e) {
                showErrorAlert("Erreur", "Impossible de supprimer la salle: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleToggleDisponibilite() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            showWarningAlert("Sélection requise", "Veuillez sélectionner une salle.");
            return;
        }

        salle.setDisponible(!salle.isDisponible());
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(salle);
            tx.commit();
            chargerSalles();
            String status = salle.isDisponible() ? "activée" : "désactivée";
            showSuccessAlert("Succès", "Salle " + status + " avec succès !");
        } catch (Exception e) {
            showErrorAlert("Erreur", "Impossible de modifier le statut de la salle: " + e.getMessage());
        }
    }

    @FXML
    private void handleAssocierEquipements() {
        Salle salle = salleTable.getSelectionModel().getSelectedItem();
        if (salle == null) {
            showWarningAlert("Sélection requise", "Veuillez sélectionner une salle.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Equipement> tous = session.createQuery("FROM Equipement", Equipement.class).list();

            if (tous.isEmpty()) {
                showWarningAlert("Aucun équipement", "Aucun équipement disponible dans le système.");
                return;
            }

            List<String> noms = tous.stream().map(Equipement::getNom).toList();
            ListView<String> listView = new ListView<>(FXCollections.observableArrayList(noms));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.setPrefHeight(200);

            // Pré-sélectionner les équipements déjà associés
            for (Equipement e : salle.getEquipements()) {
                listView.getSelectionModel().select(e.getNom());
            }

            Dialog<List<String>> dialog = new Dialog<>();
            dialog.setTitle("Associer des équipements");
            dialog.setHeaderText("Sélectionnez les équipements pour la salle \"" + salle.getNom() + "\"");
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
                        .collect(Collectors.toList());

                salle.setEquipements(new ArrayList<>(selectionnes));
                Transaction tx = session.beginTransaction();
                session.merge(salle);
                tx.commit();
                chargerSalles();
                showSuccessAlert("Succès", "Équipements associés avec succès !");
            }
        } catch (Exception e) {
            showErrorAlert("Erreur", "Impossible d'associer les équipements: " + e.getMessage());
        }
    }

    private Dialog<Salle> createSalleDialog(String title, String header, Salle existingSalle) {
        Dialog<Salle> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType saveButton = new ButtonType(existingSalle == null ? "Ajouter" : "Mettre à jour", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nomField = new TextField(existingSalle != null ? existingSalle.getNom() : "");
        TextField capaciteField = new TextField(existingSalle != null ? String.valueOf(existingSalle.getCapacite()) : "");
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("TP", "TD", "AMPHI", "CONFERENCE", "REUNION"));
        typeBox.setValue(existingSalle != null ? existingSalle.getType() : "TP");
        CheckBox dispoBox = new CheckBox("Disponible");
        dispoBox.setSelected(existingSalle == null || existingSalle.isDisponible());

        grid.add(new Label("Nom:"), 0, 0);
        grid.add(nomField, 1, 0);
        grid.add(new Label("Capacité:"), 0, 1);
        grid.add(capaciteField, 1, 1);
        grid.add(new Label("Type:"), 0, 2);
        grid.add(typeBox, 1, 2);
        grid.add(new Label("Disponibilité:"), 0, 3);
        grid.add(dispoBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButton) {
                try {
                    if (nomField.getText().trim().isEmpty()) {
                        showErrorAlert("Erreur de saisie", "Le nom de la salle est obligatoire.");
                        return null;
                    }

                    int capacite = Integer.parseInt(capaciteField.getText().trim());
                    if (capacite <= 0) {
                        showErrorAlert("Erreur de saisie", "La capacité doit être un nombre positif.");
                        return null;
                    }

                    Salle resultSalle;
                    if (existingSalle == null) {
                        resultSalle = new Salle();
                        resultSalle.setEquipements(new ArrayList<>());
                    } else {
                        resultSalle = existingSalle;
                    }

                    resultSalle.setNom(nomField.getText().trim());
                    resultSalle.setCapacite(capacite);
                    resultSalle.setType(typeBox.getValue());
                    resultSalle.setDisponible(dispoBox.isSelected());

                    return resultSalle;
                } catch (NumberFormatException e) {
                    showErrorAlert("Erreur de saisie", "La capacité doit être un nombre entier valide.");
                    return null;
                }
            }
            return null;
        });

        return dialog;
    }

    private void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarningAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) salleTable.getScene().getWindow();
        stage.close();
    }
}