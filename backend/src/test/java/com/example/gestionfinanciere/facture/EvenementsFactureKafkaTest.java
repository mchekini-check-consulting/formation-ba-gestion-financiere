package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class EvenementsFactureKafkaTest {

	private static final FactureTraitee TRAITEE = new FactureTraitee(5L, 42L, Facture.Sens.ACHAT,
			Instant.parse("2026-11-25T09:00:00Z"));
	private static final String MESSAGE = "{\"type\":\"FACTURE_TRAITEE\",\"factureId\":5,\"dossierId\":42,"
			+ "\"sens\":\"ACHAT\",\"le\":\"2026-11-25T09:00:00Z\"}";

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
	@SuppressWarnings("unchecked")
	private final ObjectProvider<KafkaTemplate<String, String>> fournisseur = mock(ObjectProvider.class);

	private EvenementsFactureKafka evenements(String bootstrap) {
		when(fournisseur.getObject()).thenReturn(kafka);
		return new EvenementsFactureKafka(fournisseur, bootstrap, "facture");
	}

	// Seulement des identifiants, jamais les informations de la facture.
	@Test
	void message() {
		assertEquals(MESSAGE, TRAITEE.message());
	}

	// Poste local, tests : sans adresse Kafka, rien n'est envoyé.
	@Test
	void sansKafka() {
		evenements(" ").factureTraitee(TRAITEE);
		verifyNoInteractions(kafka);
	}

	@Test
	void envoiSurLeTopicFacture() {
		when(kafka.send("facture", "5", MESSAGE))
				.thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
		evenements("kafka:9092").factureTraitee(TRAITEE);
		verify(kafka).send("facture", "5", MESSAGE);
	}

	// Kafka indisponible : la validation n'est pas remise en cause, seulement tracée.
	@Test
	void echecDEnvoi() {
		when(kafka.send(anyString(), anyString(), anyString()))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("indisponible")));
		assertDoesNotThrow(() -> evenements("kafka:9092").factureTraitee(TRAITEE));

		when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("bloqué"));
		assertDoesNotThrow(() -> evenements("kafka:9092").factureTraitee(TRAITEE));
	}

}
