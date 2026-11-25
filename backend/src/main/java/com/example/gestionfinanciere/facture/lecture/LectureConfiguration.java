package com.example.gestionfinanciere.facture.lecture;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * US-53 RG6 : file d'attente des lectures. Deux lectures en même temps (rendu des PDF scannés et
 * OCR Tesseract gourmands en mémoire) ; les suivantes attendent leur tour.
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

}
