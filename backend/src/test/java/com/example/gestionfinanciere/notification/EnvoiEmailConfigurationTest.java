package com.example.gestionfinanciere.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EnvoiEmailConfigurationTest {

	private final EnvoiEmailConfiguration configuration = new EnvoiEmailConfiguration();

	@Test
	void sansServeurSmtpLesEmailsSontSeulementTraces() {
		EnvoiEmail envoi = configuration.envoiEmail("", 1025, false, "", "", "noreply@pointeo.fr");

		assertThat(envoi).isInstanceOf(EnvoiEmailJournal.class);
		assertThatCode(() -> envoi.envoyer("a@exemple.fr", "sujet", "corps")).doesNotThrowAnyException();
	}

	@Test
	void avecServeurSmtpLesEmailsPartentReellement() {
		EnvoiEmail envoi = configuration.envoiEmail("mailpit", 1025, false, "", "", "noreply@pointeo.fr");

		assertThat(envoi).isInstanceOf(EnvoiEmailSmtp.class);
	}

	@Test
	void identifiantMailpitActiveLAuthentification() {
		JavaMailSenderImpl serveur = EnvoiEmailConfiguration.serveur("mailpit.mailpit.svc.cluster.local", 1025,
				false, "dev-formation-ba-gestion-financiere", "mailpit");

		assertThat(serveur.getHost()).isEqualTo("mailpit.mailpit.svc.cluster.local");
		assertThat(serveur.getPort()).isEqualTo(1025);
		assertThat(serveur.getUsername()).isEqualTo("dev-formation-ba-gestion-financiere");
		assertThat(serveur.getPassword()).isEqualTo("mailpit");
		assertThat(serveur.getDefaultEncoding()).isEqualTo("UTF-8");
		assertThat(serveur.getJavaMailProperties())
			.containsEntry("mail.smtp.auth", "true")
			.containsEntry("mail.smtp.starttls.enable", "false")
			.containsEntry("mail.smtp.connectiontimeout", EnvoiEmailConfiguration.DELAI_MS);
	}

	@Test
	void sansIdentifiantPasDAuthentification() {
		JavaMailSenderImpl serveur = EnvoiEmailConfiguration.serveur("localhost", 1025, true, "", "");

		assertThat(serveur.getUsername()).isNull();
		assertThat(serveur.getJavaMailProperties())
			.containsEntry("mail.smtp.auth", "false")
			.containsEntry("mail.smtp.starttls.enable", "true");
	}

}
