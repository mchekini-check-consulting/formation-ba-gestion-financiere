package com.example.gestionfinanciere.facture.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class ExtracteurTexteTest {

	private final MoteurOcr ocr = mock(MoteurOcr.class);
	private final ExtracteurTexte extracteur = new ExtracteurTexte(ocr);

	private static byte[] exemple(String nom) throws IOException {
		try (InputStream flux = ExtracteurTexteTest.class.getResourceAsStream("/exemples/" + nom)) {
			return flux.readAllBytes();
		}
	}

	private static byte[] pdfSansTexte(int pages) throws IOException {
		try (PDDocument document = new PDDocument()) {
			for (int i = 0; i < pages; i++) {
				document.addPage(new PDPage());
			}
			ByteArrayOutputStream sortie = new ByteArrayOutputStream();
			document.save(sortie);
			return sortie.toByteArray();
		}
	}

	// CA1 : la facture EDF d'exemple est un PDF texte : lue sans OCR, champs retrouvés.
	@Test
	void pdfTexteSansOcr() throws IOException {
		String texte = extracteur.texte(exemple("facture-electricite-octobre.pdf"), "application/pdf");

		assertTrue(texte.contains("F-2026-0611"));
		ResultatLecture lu = AnalyseurFacture.analyser(texte);
		assertEquals("EDF", lu.nomTiers());
		assertEquals(LocalDate.of(2026, 6, 12), lu.dateEmission());
		assertEquals(0, lu.montantTtc().compareTo(new java.math.BigDecimal("186.40")));
		verify(ocr, never()).lire(any());
	}

	// PDF scanné (pages sans texte) : chaque page, 3 au plus, passe par l'OCR.
	@Test
	void pdfScanne() throws IOException {
		when(ocr.lire(any())).thenReturn(Optional.of("Total TTC 12,00"));

		String texte = extracteur.texte(pdfSansTexte(4), "application/pdf");

		verify(ocr, times(ExtracteurTexte.PAGES_MAX)).lire(any());
		assertTrue(texte.contains("Total TTC 12,00"));
	}

	// Photo : OCR ; OCR sans résultat, PDF abîmé, type inconnu ou fichier absent : aucun texte.
	@Test
	void imageEtCasSansTexte() {
		byte[] png = "PNG".getBytes(StandardCharsets.US_ASCII);
		when(ocr.lire(png)).thenReturn(Optional.of("TOTAL 4,50"));
		assertEquals("TOTAL 4,50", extracteur.texte(png, "image/png"));

		when(ocr.lire(png)).thenReturn(Optional.empty());
		assertEquals("", extracteur.texte(png, "image/jpeg"));
		assertEquals("", extracteur.texte("pas un pdf".getBytes(StandardCharsets.US_ASCII), "application/pdf"));
		assertEquals("", extracteur.texte(png, "text/plain"));
		assertEquals("", extracteur.texte(null, "image/png"));
		assertEquals("", extracteur.texte(png, null));
	}

	// Tesseract absent du poste ou en erreur : aucun texte, jamais d'exception (-> « Lecture échouée »).
	@Test
	void tesseractAbsentOuEnErreur() {
		byte[] image = { 1, 2, 3 };
		assertEquals(Optional.empty(),
				new TesseractOcr("programme-ocr-introuvable-us53", Duration.ofSeconds(5)).lire(image));
		// Un programme qui existe mais refuse ces arguments (code de sortie non nul).
		String java = ProcessHandle.current().info().command().orElse("java");
		assertEquals(Optional.empty(), new TesseractOcr(java, Duration.ofSeconds(30)).lire(image));
	}

}
