package com.example.gestionfinanciere.facture.lecture;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * US-53 RG6 : file d'attente des lectures. Deux lectures en même temps (rendu des PDF scannés
 * gourmand en mémoire, appels à Claude limités) ; les suivantes attendent leur tour.
 * Choix de l'OCR (application.yaml, pointeo.ocr) : Claude si la clé de l'API Anthropic est
 * fournie (ANTHROPIC_API_KEY, secret injecté par la plateforme), sinon OcrIndisponible.
 */
@Configuration
@EnableAsync
public class LectureConfiguration {

	public static final String EXECUTEUR = "lectureFactures";

	@Bean(name = EXECUTEUR)
	Executor lectureFactures() {
		ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
		executeur.setCorePoolSize(2);
		executeur.setMaxPoolSize(2);
		executeur.setQueueCapacity(500);
		executeur.setThreadNamePrefix("lecture-facture-");
		executeur.initialize();
		return executeur;
	}

	@Bean
	MoteurOcr moteurOcr(@Value("${pointeo.ocr.claude.cle:}") String cle,
			@Value("${pointeo.ocr.claude.modele:claude-opus-5-5}") String modele,
			@Value("${pointeo.ocr.claude.adresse:}") String adresse,
			@Value("${pointeo.ocr.delai:60s}") Duration delai) {
		if (!StringUtils.hasText(cle)) {
			return new OcrIndisponible();
		}
		AnthropicOkHttpClient.Builder client = AnthropicOkHttpClient.builder().apiKey(cle).timeout(delai);
		// Adresse de l'API : vide = celle d'Anthropic (renseignée seulement par les tests).
		if (StringUtils.hasText(adresse)) {
			client.baseUrl(adresse);
		}
		return new ClaudeOcr(client.build(), modele);
	}

}
