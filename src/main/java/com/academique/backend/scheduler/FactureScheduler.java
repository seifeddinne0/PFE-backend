package com.academique.backend.scheduler;

import com.academique.backend.entity.Facture;
import com.academique.backend.repository.FactureRepository;
import com.academique.backend.service.FactureService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FactureScheduler {

    private final FactureRepository factureRepository;
    private final FactureService factureService;

    // Run every day at midnight (or frequently for testing)
    @Scheduled(cron = "0 0 0 * * *") 
    public void generateSecondTranches() {
        LocalDate today = LocalDate.now();
        
        // Target date for Tranche 2: February 15th
        if (today.getMonthValue() == 2 && today.getDayOfMonth() == 15) {
            System.out.println("📅 " + today + " : Lancement de la génération automatique des 2èmes tranches...");
            factureService.generateSecondTranchesForEligibleInvoices(today);
        }
    }
}
