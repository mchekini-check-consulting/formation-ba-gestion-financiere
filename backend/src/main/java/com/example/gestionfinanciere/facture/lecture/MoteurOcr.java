package com.example.gestionfinanciere.facture.lecture;

import java.util.Optional;

/**
 * US-53 : reconnaissance du texte d'une image (photo, scan). Implémentation : Tesseract
 * ({@link TesseractOcr}). Sert aussi plus tard aux notes de frais (US-28). Vide : rien n'a pu être lu.
 */
public interface MoteurOcr {

	// typeImage : type du fichier (image/png, image/jpeg...).
	Optional<String> lire(byte[] image, String typeImage);

}
