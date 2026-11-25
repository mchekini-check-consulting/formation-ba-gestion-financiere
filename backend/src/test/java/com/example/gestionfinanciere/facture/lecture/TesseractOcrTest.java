package com.example.gestionfinanciere.facture.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * US-53 et US-54 : lecture par Tesseract, sans le vrai programme : un faux Tesseract en Java
 * (src/test/resources/ocr/FauxTesseract.java) répond à sa place.
 */
class TesseractOcrTest {

	private static final byte[] IMAGE = "Facture F-2026-014\nTotal TTC 1 440,00".getBytes(StandardCharsets.UTF_8);

	private static TesseractOcr faux(String mode, Duration delai) {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		String programme = Path.of("src", "test", "resources", "ocr", "FauxTesseract.java").toString();
		return new TesseractOcr(List.of(java, programme, mode), delai);
	}

	// Texte reconnu renvoyé tel quel.
	@Test
	void texteLu() {
		assertEquals(Optional.of("Facture F-2026-014\nTotal TTC 1 440,00"),
				faux("texte", Duration.ofSeconds(60)).lire(IMAGE, "image/png"));
	}

	// Rien de reconnu (image blanche) : vide, « Lecture échouée ».
	@Test
	void rienDeLu() {
		assertEquals(Optional.empty(), faux("texte", Duration.ofSeconds(60)).lire(new byte[0], "image/png"));
	}

	// Tesseract en erreur (image illisible) : vide.
	@Test
	void programmeEnErreur() {
		assertEquals(Optional.empty(), faux("erreur", Duration.ofSeconds(60)).lire(IMAGE, "image/jpeg"));
	}

	// Tesseract arrêté avant d'avoir reçu toute l'image (grosse image refusée) : vide.
	@Test
	void imageRefuseeEnCoursDEnvoi() {
		assertEquals(Optional.empty(), faux("erreur", Duration.ofSeconds(60)).lire(new byte[20_000_000], "image/png"));
	}

	// Lecture interrompue (arrêt du serveur) : vide, et l'interruption est conservée.
	@Test
	void lectureInterrompue() {
		Thread.currentThread().interrupt();
		try {
			assertEquals(Optional.empty(), faux("lent", Duration.ofSeconds(60)).lire(IMAGE, "image/png"));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	// Tesseract trop long : arrêté au bout du délai, vide.
	@Test
	void programmeTropLong() {
		assertEquals(Optional.empty(), faux("lent", Duration.ofSeconds(3)).lire(IMAGE, "image/png"));
	}

	// Programme absent (poste local sans Tesseract) : vide, pas d'erreur.
	@Test
	void programmeAbsent() {
		assertEquals(Optional.empty(),
				new TesseractOcr("programme-tesseract-absent", Duration.ofSeconds(5)).lire(IMAGE, "image/png"));
	}

	// Commande de l'image : entrée et sortie standard, français et anglais.
	@Test
	void commandeTesseract() {
		assertEquals(List.of("tesseract", "stdin", "stdout", "-l", "fra+eng"),
				new TesseractOcr("tesseract", Duration.ofSeconds(60)).commande());
	}

}
