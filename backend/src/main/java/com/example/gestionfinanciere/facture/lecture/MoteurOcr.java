package com.example.gestionfinanciere.facture.lecture;

import java.util.Optional;

/**
 * US-53 : reconnaissance du texte d'une image (photo, scan). Implémentation : Tesseract
 * ({@link TesseractOcr}) ; le service retenu plus tard par l'équipe technique se branchera ici,
 * pour les factures comme pour les notes de frais (US-28). Vide : rien n'a pu être lu.
 */
public interface MoteurOcr {

	Optional<String> lire(byte[] image);

}
