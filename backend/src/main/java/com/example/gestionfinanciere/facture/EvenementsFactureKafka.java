package com.example.gestionfinanciere.facture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.gestionfinanciere.logging.EvenementMetier;

/**
 * Envoi des événements de facture sur Kafka (consigne de la plateforme : adresse du bootstrap et
 * topics déclarés dans helm.yaml, envList : KAFKA_BOOTSTRAP_SERVERS, KAFKA_TOPIC_FACTURE ; le
 * topic est créé au premier envoi).
 * Sans adresse Kafka (poste local, tests, preprod/prod pour l'instant) : rien n'est envoyé.
 * Envoyé seulement une fois la validation enregistrée en base ; un échec d'envoi ne remet pas
 * en cause la validation (tracé dans les logs métier).
 */
@Component
public class EvenementsFactureKafka {

	private static final String EVENEMENT = "kafka.facture_traitee";

	private final ObjectProvider<KafkaTemplate<String, String>> kafka;
	private final String bootstrap;
	private final String topic;

	public EvenementsFactureKafka(ObjectProvider<KafkaTemplate<String, String>> kafka,
			@Value("${pointeo.kafka.bootstrap-servers:}") String bootstrap,
			@Value("${pointeo.kafka.topic-facture:facture}") String topic) {
		this.kafka = kafka;
		this.bootstrap = bootstrap;
		this.topic = topic;
	}

	@TransactionalEventListener
	public void factureTraitee(FactureTraitee evenement) {
		if (bootstrap.isBlank()) {
			return;
		}
		try {
			kafka.getObject().send(topic, String.valueOf(evenement.factureId()), evenement.message())
					.whenComplete((resultat, erreur) -> tracer(evenement, erreur == null));
		} catch (RuntimeException e) {
			tracer(evenement, false);
		}
	}

	private void tracer(FactureTraitee evenement, boolean envoye) {
		EvenementMetier log = EvenementMetier.de(EVENEMENT, envoye ? "envoye" : "echec").avec("topic", topic)
				.avec("dossierId", evenement.dossierId()).avec("factureId", evenement.factureId());
		if (envoye) {
			log.info();
		} else {
			log.warn();
		}
	}

}
