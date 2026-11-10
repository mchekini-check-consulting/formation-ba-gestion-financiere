package com.example.gestionfinanciere.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EnvoiEmailSmtpTest {

	private final JavaMailSender serveur = mock(JavaMailSender.class);

	private final EnvoiEmailSmtp envoi = new EnvoiEmailSmtp(serveur, "noreply@pointeo.fr");

	@Test
	void envoieUnEmailTexteDepuisLExpediteurConfigure() {
		envoi.envoyer("dirigeant@exemple.fr", "Votre code de connexion Pointéo", "Bonjour, 123456");

		ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(serveur).send(message.capture());
		assertThat(message.getValue().getFrom()).isEqualTo("noreply@pointeo.fr");
		assertThat(message.getValue().getTo()).containsExactly("dirigeant@exemple.fr");
		assertThat(message.getValue().getSubject()).isEqualTo("Votre code de connexion Pointéo");
		assertThat(message.getValue().getText()).isEqualTo("Bonjour, 123456");
	}

	@Test
	void serveurInjoignableDonneEnvoiImpossible() {
		doThrow(new MailSendException("connexion refusée")).when(serveur).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> envoi.envoyer("dirigeant@exemple.fr", "sujet", "corps"))
			.isInstanceOf(EnvoiImpossibleException.class)
			.hasCauseInstanceOf(MailSendException.class);
	}

}
