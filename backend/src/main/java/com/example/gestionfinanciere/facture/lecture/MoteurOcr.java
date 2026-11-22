package com.example.gestionfinanciere.facture.lecture;

import java.util.Optional;

/**
 * US-53 : reconnaissance du texte d'une image (photo, scan). Implémentation : Claude
 * ({@link ClaudeOcr}) quand la clé de l'API Anthropic est fournie, sinon {@link OcrIndisponible}
 * (LectureConfiguration). Sert aussi plus tard aux notes de frais (US-28). Vide : rien n'a pu être lu.
 */
public interface MoteurOcr {

	// typeImage : type du fichier (image/png, image/jpeg...).
	Optional<String> lire(byte[] image, String typeImage);

}
