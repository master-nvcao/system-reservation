package com.my.project.controller;

import com.my.project.model.Equipement;
import com.my.project.model.Salle;
import com.my.project.model.Utilisateur;
import com.my.project.service.EquipementService;
import com.my.project.service.ReportService;
import com.my.project.util.HibernateUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class EquipementManagementController implements Initializable {

    // Search and filters
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private ComboBox<String> statusFilter;

    // Statistics
    @FXML private Label totalEquipmentLabel;
    @FXML private Label availableEquipmentLabel;
    @FXML private Label assignedEquipmentLabel;
    @FXML private Label equipmentTypesLabel;
    @FXML private Label mostUsedTypeLabel;

    // Table
    @FXML private TableView<EquipmentDisplay> equipementTable;
    @FXML private TableColumn<EquipmentDisplay, Long> idCol;
    @FXML private TableColumn<EquipmentDisplay, String> nomCol;
    @FXML private TableColumn<EquipmentDisplay, String> typeCol;
    @FXML private TableColumn<EquipmentDisplay, String> descCol;
    @FXML private TableColumn<EquipmentDisplay, String> statusCol;
    @FXML private TableColumn<EquipmentDisplay, Integer> sallesCountCol;
    @FXML private TableColumn<EquipmentDisplay, String> sallesCol;
    @FXML private TableColumn<EquipmentDisplay, String> lastUsedCol;
    @FXML private TableColumn<EquipmentDisplay, Void> actionsCol;

    // Info labels
    @FXML private Label tableInfoLabel;
    @FXML private Label selectionInfoLabel;

    private final EquipementService equipementService;
    private ObservableList<EquipmentDisplay> allEquipment;
    private Utilisateur currentUser;

    public EquipementManagementController() {
        this.equipementService = new EquipementService();
        this.allEquipment = FXCollections.observableArrayList();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFilters();
        setupTableColumns();
        setupTableSelection();
        loadEquipment();
        loadStatistics();
    }

    public void setCurrentUser(Utilisateur user) {
        this.currentUser = user;
    }

    private void setupFilters() {
        // Type filter
        ObservableList<String> typeOptions = FXCollections.observableArrayList(
                "Tous les types", "MULTIMEDIA", "LABORATOIRE", "FABRICATION",
                "SURVEILLANCE", "AUDIO", "VIDEO", "INFORMATIQUE"
        );
        typeFilter.setItems(typeOptions);
        typeFilter.setValue("Tous les types");

        // Status filter
        ObservableList<String> statusOptions = FXCollections.observableArrayList(
                "Tous les états", "Disponible", "Assigné", "Non utilisé"
        );
        statusFilter.setItems(statusOptions);
        statusFilter.setValue("Tous les états");

        // Real-time search
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() > 2 || newText.isEmpty()) {
                Platform.runLater(this::handleFilter);
            }
        });
    }

    private void setupTableColumns() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        nomCol.setCellValueFactory(new PropertyValueFactory<>("nom"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        sallesCountCol.setCellValueFactory(new PropertyValueFactory<>("sallesCount"));
        sallesCol.setCellValueFactory(new PropertyValueFactory<>("sallesNames"));
        lastUsedCol.setCellValueFactory(new PropertyValueFactory<>("lastUsed"));

        // Setup actions column
        setupActionsColumn();

        // Setup row styling based on status
        equipementTable.setRowFactory(tv -> {
            TableRow<EquipmentDisplay> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.getStyleClass().removeAll("row-available", "row-assigned", "row-unused");
                } else {
                    row.getStyleClass().removeAll("row-available", "row-assigned", "row-unused");
                    switch (newItem.getStatus()) {
                        case "Disponible":
                            row.getStyleClass().add("row-available");
                            break;
                        case "Assigné":
                            row.getStyleClass().add("row-assigned");
                            break;
                        case "Non utilisé":
                            row.getStyleClass().add("row-unused");
                            break;
                    }
                }
            });
            return row;
        });
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(param -> new TableCell<EquipmentDisplay, Void>() {
            private final Button editBtn = new Button("✏️");
            private final Button deleteBtn = new Button("🗑️");
            private final Button linkBtn = new Button("🔗");

            {
                editBtn.getStyleClass().add("table-action-button");
                deleteBtn.getStyleClass().add("table-action-button");
                linkBtn.getStyleClass().add("table-action-button");

                editBtn.setTooltip(new Tooltip("Modifier"));
                deleteBtn.setTooltip(new Tooltip("Supprimer"));
                linkBtn.setTooltip(new Tooltip("Associer aux salles"));

                editBtn.setOnAction(e -> {
                    EquipmentDisplay item = getTableView().getItems().get(getIndex());
                    handleEditEquipment(item);
                });

                deleteBtn.setOnAction(e -> {
                    EquipmentDisplay item = getTableView().getItems().get(getIndex());
                    handleDeleteEquipment(item);
                });

                linkBtn.setOnAction(e -> {
                    EquipmentDisplay item = getTableView().getItems().get(getIndex());
                    handleAssocierEquipment(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(3);
                    buttons.getChildren().addAll(editBtn, linkBtn, deleteBtn);
                    setGraphic(buttons);
                }
            }
        });
    }

    private void setupTableSelection() {
        equipementTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectionInfoLabel.setText(newSelection.getNom() + " (" + newSelection.getType() + ")");
            } else {
                selectionInfoLabel.setText("Aucun");
            }
        });
    }

    private void loadEquipment() {
        Task<List<Equipement>> loadTask = new Task<List<Equipement>>() {
            @Override
            protected List<Equipement> call() throws Exception {
                return equipementService.findAll();
            }

            @Override
            protected void succeeded() {
                List<Equipement> equipments = getValue();
                Platform.runLater(() -> {
                    allEquipment.clear();
                    for (Equipement eq : equipments) {
                        allEquipment.add(new EquipmentDisplay(eq));
                    }
                    equipementTable.setItems(allEquipment);
                    updateTableInfo();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showErrorDialog("Erreur de chargement",
                            "Impossible de charger les équipements: " + getException().getMessage());
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void loadStatistics() {
        Task<EquipmentStats> statsTask = new Task<EquipmentStats>() {
            @Override
            protected EquipmentStats call() throws Exception {
                int total = equipementService.getTotalEquipmentCount();
                int available = equipementService.getAvailableEquipmentCount();
                int assigned = getAssignedCount();
                int types = equipementService.getAllEquipmentTypes().size();
                String mostUsed = equipementService.getMostRequestedEquipment();

                return new EquipmentStats(total, available, assigned, types, mostUsed);
            }

            @Override
            protected void succeeded() {
                EquipmentStats stats = getValue();
                Platform.runLater(() -> updateStatisticsUI(stats));
            }
        };

        Thread statsThread = new Thread(statsTask);
        statsThread.setDaemon(true);
        statsThread.start();
    }

    private int getAssignedCount() {
        return (int) allEquipment.stream()
                .filter(eq -> eq.getSallesCount() > 0)
                .count();
    }

    private void updateStatisticsUI(EquipmentStats stats) {
        totalEquipmentLabel.setText(String.valueOf(stats.total));
        availableEquipmentLabel.setText(String.valueOf(stats.available));
        assignedEquipmentLabel.setText(String.valueOf(stats.assigned));
        equipmentTypesLabel.setText(String.valueOf(stats.types));
        mostUsedTypeLabel.setText(stats.mostUsed);
    }

    @FXML
    private void handleFilter() {
        String searchText = searchField.getText().trim().toLowerCase();
        String selectedType = typeFilter.getValue();
        String selectedStatus = statusFilter.getValue();

        ObservableList<EquipmentDisplay> filteredList = FXCollections.observableArrayList();

        for (EquipmentDisplay eq : allEquipment) {
            boolean matches = true;

            // Search filter
            if (!searchText.isEmpty()) {
                boolean nameMatch = eq.getNom().toLowerCase().contains(searchText);
                boolean descMatch = eq.getDescription().toLowerCase().contains(searchText);
                if (!nameMatch && !descMatch) {
                    matches = false;
                }
            }

            // Type filter
            if (selectedType != null && !selectedType.equals("Tous les types") &&
                    !eq.getType().equals(selectedType)) {
                matches = false;
            }

            // Status filter
            if (selectedStatus != null && !selectedStatus.equals("Tous les états") &&
                    !eq.getStatus().equals(selectedStatus)) {
                matches = false;
            }

            if (matches) {
                filteredList.add(eq);
            }
        }

        equipementTable.setItems(filteredList);
        updateTableInfo();
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        typeFilter.setValue("Tous les types");
        statusFilter.setValue("Tous les états");
        equipementTable.setItems(allEquipment);
        updateTableInfo();
    }

    @FXML
    private void handleRefresh() {
        loadEquipment();
        loadStatistics();
    }

    @FXML
    private void handleAdd() {
        Dialog<Equipement> dialog = createEquipmentDialog("Ajouter un Équipement", null);

        Optional<Equipement> result = dialog.showAndWait();
        result.ifPresent(this::saveEquipment);
    }

    @FXML
    private void handleEdit() {
        EquipmentDisplay selected = equipementTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner un équipement à modifier.");
            return;
        }
        handleEditEquipment(selected);
    }

    private void handleEditEquipment(EquipmentDisplay equipmentDisplay) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Equipement equipment = session.get(Equipement.class, equipmentDisplay.getId());
            if (equipment == null) {
                showErrorDialog("Erreur", "Équipement introuvable.");
                return;
            }

            Dialog<Equipement> dialog = createEquipmentDialog("Modifier l'Équipement", equipment);

            Optional<Equipement> result = dialog.showAndWait();
            result.ifPresent(this::updateEquipment);
        }
    }

    private Dialog<Equipement> createEquipmentDialog(String title, Equipement equipment) {
        Dialog<Equipement> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(equipment == null ? "Ajout d'un nouvel équipement" :
                "Modification de: " + equipment.getNom());

        ButtonType saveBtn = new ButtonType(equipment == null ? "Ajouter" : "Mettre à jour",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // Form fields
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        TextField nomField = new TextField(equipment != null ? equipment.getNom() : "");
        TextArea descField = new TextArea(equipment != null ? equipment.getDescription() : "");
        descField.setPrefRowCount(3);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("MULTIMEDIA", "LABORATOIRE", "FABRICATION",
                "SURVEILLANCE", "AUDIO", "VIDEO", "INFORMATIQUE");
        if (equipment != null) {
            typeCombo.setValue(equipment.getType());
        }

        grid.addRow(0, new Label("Nom:"), nomField);
        grid.addRow(1, new Label("Type:"), typeCombo);
        grid.addRow(2, new Label("Description:"), descField);

        dialog.getDialogPane().setContent(grid);

        // Validation
        nomField.textProperty().addListener((obs, oldText, newText) -> {
            dialog.getDialogPane().lookupButton(saveBtn).setDisable(newText.trim().isEmpty());
        });

        dialog.setResultConverter(button -> {
            if (button == saveBtn) {
                if (equipment == null) {
                    Equipement newEq = new Equipement();
                    newEq.setNom(nomField.getText().trim());
                    newEq.setDescription(descField.getText().trim());
                    newEq.setType(typeCombo.getValue());
                    return newEq;
                } else {
                    equipment.setNom(nomField.getText().trim());
                    equipment.setDescription(descField.getText().trim());
                    equipment.setType(typeCombo.getValue());
                    return equipment;
                }
            }
            return null;
        });

        return dialog;
    }

    private void saveEquipment(Equipement equipment) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(equipment);
            tx.commit();
            loadEquipment();
            showSuccessDialog("Succès", "Équipement ajouté avec succès.");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de l'ajout: " + e.getMessage());
        }
    }

    private void updateEquipment(Equipement equipment) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(equipment);
            tx.commit();
            loadEquipment();
            showSuccessDialog("Succès", "Équipement modifié avec succès.");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de la modification: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        EquipmentDisplay selected = equipementTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner un équipement à supprimer.");
            return;
        }
        handleDeleteEquipment(selected);
    }

    private void handleDeleteEquipment(EquipmentDisplay equipmentDisplay) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation de suppression");
        confirmation.setHeaderText("Supprimer l'équipement");
        confirmation.setContentText("Êtes-vous sûr de vouloir supprimer cet équipement ?\n\n" +
                "Nom: " + equipmentDisplay.getNom() + "\n" +
                "Type: " + equipmentDisplay.getType() + "\n" +
                "Salles associées: " + equipmentDisplay.getSallesCount());

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                Equipement equipment = session.get(Equipement.class, equipmentDisplay.getId());
                if (equipment != null) {
                    // Remove associations with rooms
                    List<Salle> salles = session.createQuery(
                                    "FROM Salle s WHERE :eq MEMBER OF s.equipements", Salle.class)
                            .setParameter("eq", equipment)
                            .list();

                    for (Salle salle : salles) {
                        salle.getEquipements().remove(equipment);
                        session.merge(salle);
                    }

                    session.remove(equipment);
                    tx.commit();
                    loadEquipment();
                    showSuccessDialog("Succès", "Équipement supprimé avec succès.");
                } else {
                    showErrorDialog("Erreur", "Équipement introuvable.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog("Erreur", "Erreur lors de la suppression: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleAssocierSalles() {
        EquipmentDisplay selected = equipementTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner un équipement.");
            return;
        }
        handleAssocierEquipment(selected);
    }

    private void handleAssocierEquipment(EquipmentDisplay equipmentDisplay) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Equipement equipment = session.get(Equipement.class, equipmentDisplay.getId());
            List<Salle> allRooms = session.createQuery("FROM Salle", Salle.class).list();

            // Create selection dialog
            Dialog<List<String>> dialog = new Dialog<>();
            dialog.setTitle("Associer aux Salles");
            dialog.setHeaderText("Sélectionnez les salles pour: " + equipment.getNom());

            ListView<String> listView = new ListView<>();
            listView.getItems().addAll(allRooms.stream().map(Salle::getNom).collect(Collectors.toList()));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.setPrefHeight(300);

            // Pre-select associated rooms
            List<Salle> associatedRooms = session.createQuery(
                            "FROM Salle s WHERE :eq MEMBER OF s.equipements", Salle.class)
                    .setParameter("eq", equipment)
                    .list();

            for (Salle salle : associatedRooms) {
                listView.getSelectionModel().select(salle.getNom());
            }

            VBox content = new VBox(10);
            content.getChildren().addAll(
                    new Label("Salles disponibles:"),
                    listView,
                    new Label("Maintenez Ctrl pour sélectionner plusieurs salles")
            );

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    return new ArrayList<>(listView.getSelectionModel().getSelectedItems());
                }
                return null;
            });

            Optional<List<String>> result = dialog.showAndWait();
            if (result.isPresent()) {
                updateRoomAssociations(equipment, result.get(), allRooms, session);
            }
        }
    }

    private void updateRoomAssociations(Equipement equipment, List<String> selectedRoomNames,
                                        List<Salle> allRooms, Session session) {
        try {
            Transaction tx = session.beginTransaction();

            for (Salle salle : allRooms) {
                if (selectedRoomNames.contains(salle.getNom())) {
                    if (!salle.getEquipements().contains(equipment)) {
                        salle.getEquipements().add(equipment);
                    }
                } else {
                    salle.getEquipements().remove(equipment);
                }
                session.merge(salle);
            }

            tx.commit();
            loadEquipment();
            showSuccessDialog("Succès", "Associations mises à jour avec succès.");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Erreur", "Erreur lors de la mise à jour des associations: " + e.getMessage());
        }
    }

    @FXML
    private void handleDuplicate() {
        EquipmentDisplay selected = equipementTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningDialog("Sélection requise", "Veuillez sélectionner un équipement à dupliquer.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Equipement original = session.get(Equipement.class, selected.getId());

            Equipement duplicate = new Equipement();
            duplicate.setNom(original.getNom() + " - Copie");
            duplicate.setDescription(original.getDescription());
            duplicate.setType(original.getType());

            Dialog<Equipement> dialog = createEquipmentDialog("Dupliquer l'Équipement", duplicate);
            Optional<Equipement> result = dialog.showAndWait();
            result.ifPresent(this::saveEquipment);
        }
    }

    @FXML
    private void handleShowStats() {
        // Create statistics dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Statistiques des Équipements");
        dialog.setHeaderText("Aperçu détaillé des équipements");

        VBox content = new VBox(15);
        content.setPrefWidth(400);

        try {
            Map<String, Integer> typeStats = equipementService.getEquipmentStatsByType();
            Map<String, Integer> usageStats = equipementService.getEquipmentUsageStats();

            // Type distribution
            VBox typeSection = new VBox(5);
            typeSection.getChildren().add(new Label("Distribution par Type:"));
            for (Map.Entry<String, Integer> entry : typeStats.entrySet()) {
                Label statLabel = new Label("• " + entry.getKey() + ": " + entry.getValue());
                typeSection.getChildren().add(statLabel);
            }

            // Usage statistics
            VBox usageSection = new VBox(5);
            usageSection.getChildren().add(new Label("Équipements les Plus Utilisés:"));
            usageStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> {
                        Label usageLabel = new Label("• " + entry.getKey() + ": " + entry.getValue() + " salle(s)");
                        usageSection.getChildren().add(usageLabel);
                    });

            content.getChildren().addAll(typeSection, new Separator(), usageSection);
        } catch (Exception e) {
            content.getChildren().add(new Label("Erreur lors du chargement des statistiques."));
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter les équipements");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichier PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Fichier Excel", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(equipementTable.getScene().getWindow());
        if (file != null) {
            exportEquipment(file);
        }
    }

    private void exportEquipment(File file) {
        Task<String> exportTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                ReportService reportService = new ReportService();
                if (file.getName().endsWith(".pdf")) {
                    return reportService.generatePDFReport("equipements");
                } else {
                    return reportService.generateExcelReport("equipements");
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    showSuccessDialog("Export réussi", "Fichier exporté: " + getValue());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showErrorDialog("Erreur d'export", "Erreur lors de l'export: " + getException().getMessage());
                });
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) equipementTable.getScene().getWindow();
        stage.close();
    }

    private void updateTableInfo() {
        int total = equipementTable.getItems().size();
        tableInfoLabel.setText("Affichage de " + total + " équipement" + (total > 1 ? "s" : ""));
    }

    // Utility methods for dialogs
    private void showSuccessDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarningDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Equipment display class for table
    public static class EquipmentDisplay {
        private final Long id;
        private final String nom;
        private final String type;
        private final String description;
        private final String status;
        private final Integer sallesCount;
        private final String sallesNames;
        private final String lastUsed;

        public EquipmentDisplay(Equipement equipment) {
            this.id = equipment.getId();
            this.nom = equipment.getNom();
            this.type = equipment.getType() != null ? equipment.getType() : "Non spécifié";
            this.description = equipment.getDescription() != null ? equipment.getDescription() : "";

            // Calculate associated rooms count (this would need to be done in a service method)
            this.sallesCount = getSallesCountForEquipment(equipment);
            this.sallesNames = getSallesNamesForEquipment(equipment);

            // Determine status
            if (sallesCount > 0) {
                this.status = "Assigné";
            } else {
                this.status = "Non utilisé";
            }

            // Mock last used date - you could implement this based on reservation data
            this.lastUsed = "N/A";
        }

        private Integer getSallesCountForEquipment(Equipement equipment) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Long count = session.createQuery(
                                "SELECT COUNT(s) FROM Salle s WHERE :eq MEMBER OF s.equipements", Long.class)
                        .setParameter("eq", equipment)
                        .uniqueResult();
                return count.intValue();
            } catch (Exception e) {
                return 0;
            }
        }

        private String getSallesNamesForEquipment(Equipement equipment) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                List<String> names = session.createQuery(
                                "SELECT s.nom FROM Salle s WHERE :eq MEMBER OF s.equipements", String.class)
                        .setParameter("eq", equipment)
                        .list();
                return String.join(", ", names);
            } catch (Exception e) {
                return "";
            }
        }

        // Getters
        public Long getId() { return id; }
        public String getNom() { return nom; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public Integer getSallesCount() { return sallesCount; }
        public String getSallesNames() { return sallesNames; }
        public String getLastUsed() { return lastUsed; }
    }

    // Statistics data class
    private static class EquipmentStats {
        final int total;
        final int available;
        final int assigned;
        final int types;
        final String mostUsed;

        EquipmentStats(int total, int available, int assigned, int types, String mostUsed) {
            this.total = total;
            this.available = available;
            this.assigned = assigned;
            this.types = types;
            this.mostUsed = mostUsed;
        }
    }
}