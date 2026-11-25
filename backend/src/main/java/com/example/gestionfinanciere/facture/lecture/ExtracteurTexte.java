package com.example.gestionfinanciere.facture.lecture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * US-53 : texte d'une facture. PDF avec texte : lu directement (PDFBox), sans OCR. PDF scanné
 * (sans texte) : pages rendues en image puis OCR. Photo (JPG, PNG) : OCR. Fichier illisible : vide.
 */
@Component
public class ExtracteurTexte {

	static final String PDF = "application/pdf";
	static final String PNG = "image/png";
	// Une facture tient sur peu de pages : on n'envoie pas un long document entier à l'OCR.
	static final int PAGES_MAX = 3;
	// Résolution conseillée pour Tesseract (A4 : 2480 x 3508 points).
	private static final float RESOLUTION = 300;

	private final MoteurOcr ocr;

	public ExtracteurTexte(MoteurOcr ocr) {
		this.ocr = ocr;
	}

	public String texte(byte[] contenu, String typeFichier) {
		if (contenu == null || typeFichier == null) {
			return "";
		}
		if (PDF.equals(typeFichier)) {
			return textePdf(contenu);
		}
		return typeFichier.startsWith("image/") ? ocr.lire(contenu, typeFichier).orElse("") : "";
	}

	private String textePdf(byte[] contenu) {
		try (PDDocument document = Loader.loadPDF(contenu)) {
			String texte = new PDFTextStripper().getText(document);
			if (!texte.isBlank()) {
				return texte;
			}
			StringBuilder lu = new StringBuilder();
			PDFRenderer rendu = new PDFRenderer(document);
			for (int page = 0; page < Math.min(document.getNumberOfPages(), PAGES_MAX); page++) {
				BufferedImage image = rendu.renderImageWithDPI(page, RESOLUTION, ImageType.GRAY);
				ByteArrayOutputStream png = new ByteArrayOutputStream();
				ImageIO.write(image, "png", png);
				ocr.lire(png.toByteArray(), PNG).ifPresent(texteOcr -> lu.append(texteOcr).append('\n'));
			}
			return lu.toString();
		} catch (IOException e) {
			// PDF abîmé ou protégé : rien à lire.
			return "";
		}
	}

}
