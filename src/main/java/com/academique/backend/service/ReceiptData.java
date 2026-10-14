package com.academique.backend.service;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class ReceiptData {
    private String ribDestinataire;
    private String codePaiement;
    private Double montant;
    private LocalDate datePaiement;
    private String banqueEmettrice;
}
