package com.example.gestionfinanciere.facture.lecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * US-53 : OCR par le programme Tesseract (open source), installé dans l'image du backend
 * (Dockerfile) avec le français et l'anglais. L'image passe par l'entrée standard et le texte
 * revient par la sortie standard : aucun fichier écrit. Programme absent (ex. poste local sans
 * Tesseract), en erreur ou trop long : vide, la facture passe en « Lecture échouée ».
 */
@Component
public class TesseractOcr implements MoteurOcr {

	private static final Logger LOG = LoggerFactory.getLogger(TesseractOcr.class);

	private final List<String> commande;
	private final Duration delai;

	public TesseractOcr(@Value("${pointeo.ocr.tesseract:tesseract}") String programme,
			@Value("${pointeo.ocr.delai:60s}") Duration delai) {
		this.commande = List.of(programme, "stdin", "stdout", "-l", "fra+eng");
		this.delai = delai;
	}

	@Override
	public Optional<String> lire(byte[] image) {
		Process processus;
		try {
			processus = new ProcessBuilder(commande).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		} catch (IOException e) {
			LOG.warn("OCR indisponible : programme Tesseract introuvable");
			return Optional.empty();
		}
		CompletableFuture<String> sortie = CompletableFuture.supplyAsync(() -> lireTout(processus.getInputStream()));
		try (OutputStream entree = processus.getOutputStream()) {
			entree.write(image);
		} catch (IOException e) {
			// Tesseract a refusé l'image (fermé avant la fin) : son code de sortie le dira.
			LOG.debug("OCR : entrée interrompue");
		}
		try {
			String texte = sortie.get(delai.toMillis(), TimeUnit.MILLISECONDS);
			boolean fini = processus.waitFor(delai.toMillis(), TimeUnit.MILLISECONDS);
			return fini && processus.exitValue() == 0 ? Optional.of(texte) : Optional.empty();
		} catch (TimeoutException | ExecutionException e) {
			LOG.warn("OCR en échec ou trop long");
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		} finally {
			processus.destroyForcibly();
		}
	}

	private static String lireTout(InputStream flux) {
		try {
			return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
