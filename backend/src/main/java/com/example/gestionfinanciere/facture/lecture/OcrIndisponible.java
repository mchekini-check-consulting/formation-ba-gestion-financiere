package com.example.gestionfinanciere.facture.lecture;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * US-53 : pas de clé de l'API Anthropic (poste local, environnement où la plateforme ne l'a pas
 * encore injectée) : aucune photo ni PDF scanné n'est lu, la facture passe en « Lecture échouée »
 * et se saisit à la main. Les PDF avec texte restent lus sans OCR (ExtracteurTexte).
 */
public class OcrIndisponible implements MoteurOcr {

	private static final Logger LOG = LoggerFactory.getLogger(OcrIndisponible.class);

	@Override
	public Optional<String> lire(byte[] image, String typeImage) {
		LOG.warn("OCR indisponible : clé de l'API Anthropic (ANTHROPIC_API_KEY) absente");
		return Optional.empty();
	}

}
