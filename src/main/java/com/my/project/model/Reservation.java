package com.my.project.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Utilisateur utilisateur;

    @ManyToOne
    private Salle salle;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;

    private String description;

    @Enumerated(EnumType.STRING)
    private StatutReservation statut = StatutReservation.EN_ATTENTE;

    private LocalDateTime dateCreation = LocalDateTime.now();

    private LocalDateTime dateValidation;

    @ManyToOne
    private Utilisateur validateurAdmin;

    private String commentaireAdmin;

    public enum StatutReservation {
        EN_ATTENTE,    // Waiting for admin approval
        APPROUVEE,     // Approved by admin
        REJETEE,       // Rejected by admin
        ANNULEE        // Cancelled by user
    }
}