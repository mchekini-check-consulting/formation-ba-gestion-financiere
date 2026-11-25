package com.example.gestionfinanciere.facture;

import java.time.Instant;

/**
 * US-55 : une facture vient d'être validée (statut « Traitée »). Publié dans l'application, puis
 * envoyé sur le topic Kafka « facture » une fois la validation enregistrée (EvenementsFactureKafka).
 * Seulement des identifiants : jamais de donnée de la facture (tiers, montants).
 */
public record FactureTraitee(Long factureId, Long dossierId, Facture.Sens sens, Instant le) {

	/** Message Kafka, en JSON. */
	String message() {
		return "{\"type\":\"FACTURE_TRAITEE\",\"factureId\":" + factureId + ",\"dossierId\":" + dossierId
				+ ",\"sens\":\"" + sens + "\",\"le\":\"" + le + "\"}";
	}

}
