package com.my.project.service;

import com.itextpdf.text.Font;
import com.my.project.model.Reservation;
import com.my.project.model.Utilisateur;
import com.my.project.model.Salle;
import com.my.project.model.Equipement;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReportService {

    private final UserService userService;
    private final ReservationService reservationService;
    private final SalleService salleService;
    private final EquipementService equipementService;
    private final DashboardService dashboardService;

    public ReportService() {
        this.userService = new UserService();
        this.reservationService = new ReservationService();
        this.salleService = new SalleService();
        this.equipementService = new EquipementService();
        this.dashboardService = new DashboardService();
    }

    /**
     * Generate comprehensive PDF report
     */
    public String generatePDFReport(String reportType) throws DocumentException, IOException {
        String fileName = generateFileName("rapport_" + reportType, "pdf");
        Document document = new Document(PageSize.A4);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));

        document.open();

        // Add header
        addPDFHeader(document, reportType);

        // Add content based on report type
        switch (reportType.toLowerCase()) {
            case "complet":
                addCompleteReport(document);
                break;
            case "reservations":
                addReservationsReport(document);
                break;
            case "utilisateurs":
                addUsersReport(document);
                break;
            case "salles":
                addRoomsReport(document);
                break;
            case "equipements":
                addEquipmentReport(document);
                break;
            default:
                addCompleteReport(document);
        }

        // Add footer
        addPDFFooter(document);

        document.close();
        return fileName;
    }

    /**
     * Generate Excel report
     */
    public String generateExcelReport(String reportType) throws IOException {
        String fileName = generateFileName("rapport_" + reportType, "xlsx");
        Workbook workbook = new XSSFWorkbook();

        switch (reportType.toLowerCase()) {
            case "complet":
                createCompleteExcelReport(workbook);
                break;
            case "reservations":
                createReservationsExcelSheet(workbook, "Réservations");
                break;
            case "utilisateurs":
                createUsersExcelSheet(workbook, "Utilisateurs");
                break;
            case "salles":
                createRoomsExcelSheet(workbook, "Salles");
                break;
            case "equipements":
                createEquipmentExcelSheet(workbook, "Équipements");
                break;
            default:
                createCompleteExcelReport(workbook);
        }

        FileOutputStream outputStream = new FileOutputStream(fileName);
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();

        return fileName;
    }

    // PDF Report Methods
    private void addPDFHeader(Document document, String reportType) throws DocumentException {
        // Title
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.DARK_GRAY);
        Paragraph title = new Paragraph("Rapport " + capitalizeFirst(reportType), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        // Date and time
        Font dateFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.GRAY);
        Paragraph date = new Paragraph("Généré le: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")), dateFont);
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingAfter(20);
        document.add(date);

        // Separator line
        document.add(new Paragraph(" "));
    }

    private void addCompleteReport(Document document) throws DocumentException {
        // Statistics Summary
        addStatisticsSummary(document);

        // Recent Reservations
        addRecentReservations(document);

        // User Statistics
        addUserStatistics(document);

        // Room Utilization
        addRoomUtilization(document);

        // Equipment Status
        addEquipmentStatus(document);
    }

    private void addStatisticsSummary(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        document.add(new Paragraph("📊 Résumé Statistiques", sectionFont));
        document.add(new Paragraph(" "));

        Map<String, Integer> stats = dashboardService.getBasicStats();

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableRow(table, "Utilisateurs Actifs", stats.get("activeUsers").toString());
        addTableRow(table, "Salles Disponibles", stats.get("availableRooms").toString());
        addTableRow(table, "Réservations Actives", stats.get("activeReservations").toString());
        addTableRow(table, "Demandes en Attente", stats.get("pendingRequests").toString());

        document.add(table);
    }

    private void addReservationsReport(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        document.add(new Paragraph("📋 Rapport des Réservations", sectionFont));
        document.add(new Paragraph(" "));

        List<Reservation> reservations = reservationService.getUpcomingReservations(20);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        // Headers
        addTableHeader(table, "Utilisateur");
        addTableHeader(table, "Salle");
        addTableHeader(table, "Date Début");
        addTableHeader(table, "Date Fin");

        // Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (Reservation reservation : reservations) {
            addTableCell(table, reservation.getUtilisateur().getNom());
            addTableCell(table, reservation.getSalle().getNom());
            addTableCell(table, reservation.getDateDebut().format(formatter));
            addTableCell(table, reservation.getDateFin().format(formatter));
        }

        document.add(table);
    }

    private void addRecentReservations(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("Réservations Récentes (10 dernières)", sectionFont));
        document.add(new Paragraph(" "));

        List<Reservation> recentReservations = reservationService.getUpcomingReservations(10);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableHeader(table, "Utilisateur");
        addTableHeader(table, "Salle");
        addTableHeader(table, "Date");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (Reservation reservation : recentReservations) {
            addTableCell(table, reservation.getUtilisateur().getNom());
            addTableCell(table, reservation.getSalle().getNom());
            addTableCell(table, reservation.getDateDebut().format(formatter));
        }

        document.add(table);
    }

    private void addUserStatistics(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("👥 Statistiques Utilisateurs", sectionFont));
        document.add(new Paragraph(" "));

        UserService.UserStats userStats = userService.getUserStats();

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setSpacingAfter(15);

        addTableRow(table, "Total Utilisateurs", String.valueOf(userStats.getTotalUsers()));
        addTableRow(table, "Administrateurs", String.valueOf(userStats.getAdmins()));
        addTableRow(table, "Professeurs", String.valueOf(userStats.getProfessors()));
        addTableRow(table, "Étudiants", String.valueOf(userStats.getStudents()));

        document.add(table);
    }

    private void addRoomUtilization(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("🏢 Utilisation des Salles", sectionFont));
        document.add(new Paragraph(" "));

        SalleService.RoomStats roomStats = salleService.getRoomStats();

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setSpacingAfter(15);

        addTableRow(table, "Total Salles", String.valueOf(roomStats.getTotalRooms()));
        addTableRow(table, "Salles Disponibles", String.valueOf(roomStats.getAvailableRooms()));
        addTableRow(table, "Salles Occupées", String.valueOf(roomStats.getOccupiedRooms()));
        addTableRow(table, "Taux d'Occupation", String.format("%.1f%%", roomStats.getOccupancyPercentage()));

        document.add(table);
    }

    private void addEquipmentStatus(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("🖥️ État des Équipements", sectionFont));
        document.add(new Paragraph(" "));

        EquipementService.EquipmentStats equipStats = equipementService.getEquipmentStats();

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setSpacingAfter(15);

        addTableRow(table, "Total Équipements", String.valueOf(equipStats.getTotalEquipment()));
        addTableRow(table, "Équipements Disponibles", String.valueOf(equipStats.getAvailableEquipment()));
        addTableRow(table, "Équipements Non Utilisés", String.valueOf(equipStats.getUnusedEquipment()));
        addTableRow(table, "Plus Demandé", equipStats.getMostRequested());

        document.add(table);
    }

    private void addUsersReport(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        document.add(new Paragraph("👥 Rapport des Utilisateurs", sectionFont));
        document.add(new Paragraph(" "));

        List<Utilisateur> users = userService.findAll();

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableHeader(table, "Nom");
        addTableHeader(table, "Email");
        addTableHeader(table, "Rôle");

        for (Utilisateur user : users) {
            addTableCell(table, user.getNom());
            addTableCell(table, user.getEmail());
            addTableCell(table, user.getRole());
        }

        document.add(table);
    }

    private void addRoomsReport(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        document.add(new Paragraph("🏢 Rapport des Salles", sectionFont));
        document.add(new Paragraph(" "));

        List<Salle> salles = salleService.findAll();

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableHeader(table, "Nom");
        addTableHeader(table, "Capacité");
        addTableHeader(table, "Type");
        addTableHeader(table, "Disponible");

        for (Salle salle : salles) {
            addTableCell(table, salle.getNom());
            addTableCell(table, String.valueOf(salle.getCapacite()));
            addTableCell(table, salle.getType());
            addTableCell(table, salle.isDisponible() ? "Oui" : "Non");
        }

        document.add(table);
    }

    private void addEquipmentReport(Document document) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        document.add(new Paragraph("🖥️ Rapport des Équipements", sectionFont));
        document.add(new Paragraph(" "));

        List<Equipement> equipements = equipementService.findAll();

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableHeader(table, "Nom");
        addTableHeader(table, "Type");
        addTableHeader(table, "Description");

        for (Equipement equipement : equipements) {
            addTableCell(table, equipement.getNom());
            addTableCell(table, equipement.getType());
            addTableCell(table, equipement.getDescription() != null ? equipement.getDescription() : "");
        }

        document.add(table);
    }

    private void addPDFFooter(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
        Font footerFont = new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, BaseColor.GRAY);
        Paragraph footer = new Paragraph("Rapport généré automatiquement par Admin Hub - " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    // Excel Report Methods
    private void createCompleteExcelReport(Workbook workbook) {
        createReservationsExcelSheet(workbook, "Réservations");
        createUsersExcelSheet(workbook, "Utilisateurs");
        createRoomsExcelSheet(workbook, "Salles");
        createEquipmentExcelSheet(workbook, "Équipements");
        createStatisticsExcelSheet(workbook, "Statistiques");
    }

    private void createReservationsExcelSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Utilisateur");
        headerRow.createCell(2).setCellValue("Salle");
        headerRow.createCell(3).setCellValue("Date Début");
        headerRow.createCell(4).setCellValue("Date Fin");
        headerRow.createCell(5).setCellValue("Description");

        // Data
        List<Reservation> reservations = reservationService.findAll();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (int i = 0; i < reservations.size(); i++) {
            Reservation reservation = reservations.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(reservation.getId());
            row.createCell(1).setCellValue(reservation.getUtilisateur().getNom());
            row.createCell(2).setCellValue(reservation.getSalle().getNom());
            row.createCell(3).setCellValue(reservation.getDateDebut().format(formatter));
            row.createCell(4).setCellValue(reservation.getDateFin().format(formatter));
            row.createCell(5).setCellValue(reservation.getDescription() != null ? reservation.getDescription() : "");
        }

        // Auto-size columns
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createUsersExcelSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Nom");
        headerRow.createCell(2).setCellValue("Email");
        headerRow.createCell(3).setCellValue("Rôle");

        // Data
        List<Utilisateur> users = userService.findAll();

        for (int i = 0; i < users.size(); i++) {
            Utilisateur user = users.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(user.getId());
            row.createCell(1).setCellValue(user.getNom());
            row.createCell(2).setCellValue(user.getEmail());
            row.createCell(3).setCellValue(user.getRole());
        }

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRoomsExcelSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Nom");
        headerRow.createCell(2).setCellValue("Capacité");
        headerRow.createCell(3).setCellValue("Type");
        headerRow.createCell(4).setCellValue("Disponible");

        // Data
        List<Salle> salles = salleService.findAll();

        for (int i = 0; i < salles.size(); i++) {
            Salle salle = salles.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(salle.getId());
            row.createCell(1).setCellValue(salle.getNom());
            row.createCell(2).setCellValue(salle.getCapacite());
            row.createCell(3).setCellValue(salle.getType());
            row.createCell(4).setCellValue(salle.isDisponible() ? "Oui" : "Non");
        }

        // Auto-size columns
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createEquipmentExcelSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Nom");
        headerRow.createCell(2).setCellValue("Type");
        headerRow.createCell(3).setCellValue("Description");

        // Data
        List<Equipement> equipements = equipementService.findAll();

        for (int i = 0; i < equipements.size(); i++) {
            Equipement equipement = equipements.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(equipement.getId());
            row.createCell(1).setCellValue(equipement.getNom());
            row.createCell(2).setCellValue(equipement.getType());
            row.createCell(3).setCellValue(equipement.getDescription() != null ? equipement.getDescription() : "");
        }

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createStatisticsExcelSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Métrique");
        headerRow.createCell(1).setCellValue("Valeur");

        // Data
        Map<String, Integer> stats = dashboardService.getBasicStats();

        int rowNum = 1;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(translateStatKey(entry.getKey()));
            row.createCell(1).setCellValue(entry.getValue());
        }

        // Auto-size columns
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    // Utility methods
    private void addTableHeader(PdfPTable table, String text) {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
        cell.setBackgroundColor(BaseColor.DARK_GRAY);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text) {
        Font cellFont = new Font(Font.FontFamily.HELVETICA, 9);
        PdfPCell cell = new PdfPCell(new Phrase(text, cellFont));
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        addTableCell(table, label);
        addTableCell(table, value);
    }

    private String generateFileName(String baseName, String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return baseName + "_" + timestamp + "." + extension;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String translateStatKey(String key) {
        switch (key) {
            case "activeUsers": return "Utilisateurs Actifs";
            case "availableRooms": return "Salles Disponibles";
            case "activeReservations": return "Réservations Actives";
            case "pendingRequests": return "Demandes en Attente";
            default: return key;
        }
    }
}