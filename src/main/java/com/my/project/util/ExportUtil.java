package com.my.project.util;

import com.my.project.model.Reservation;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ExportUtil {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static void exportPDF(List<Reservation> reservations, File file) {
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(file));
            doc.open();
            doc.add(new Paragraph("Liste des réservations\n\n"));

            for (Reservation r : reservations) {
                String line = String.format(
                        "Salle: %s\nUtilisateur: %s\nDébut: %s\nFin: %s\nDescription: %s\n\n",
                        r.getSalle().getNom(),
                        r.getUtilisateur().getEmail(),
                        r.getDateDebut().format(formatter),
                        r.getDateFin().format(formatter),
                        r.getDescription());
                doc.add(new Paragraph(line));
            }

        } catch (DocumentException | java.io.IOException e) {
            e.printStackTrace();
        } finally {
            doc.close();
        }
    }

    public static void exportExcel(List<Reservation> reservations, File file) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Réservations");

            Row header = sheet.createRow(0);
            String[] titles = {"Salle", "Utilisateur", "Date début", "Date fin", "Description"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }

            int rowNum = 1;
            for (Reservation r : reservations) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.getSalle().getNom());
                row.createCell(1).setCellValue(r.getUtilisateur().getEmail());
                row.createCell(2).setCellValue(r.getDateDebut().format(formatter));
                row.createCell(3).setCellValue(r.getDateFin().format(formatter));
                row.createCell(4).setCellValue(r.getDescription());
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
