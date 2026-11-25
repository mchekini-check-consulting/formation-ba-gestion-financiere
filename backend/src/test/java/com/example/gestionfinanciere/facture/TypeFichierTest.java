package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

// US-15 RG1 : PDF, JPG ou PNG reconnus d'après leur contenu.
class TypeFichierTest {

	// Fichiers d'exemple des tests (src/test/resources/exemples).
	private static byte[] exemple(String nom) throws IOException {
		try (InputStream flux = TypeFichierTest.class.getResourceAsStream("/exemples/" + nom)) {
			return flux.readAllBytes();
		}
	}

	@Test
	void pdfJpgPng() throws IOException {
		assertEquals(Optional.of("application/pdf"),
				TypeFichier.reconnaitre("%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII)));
		assertEquals(Optional.of("image/jpeg"),
				TypeFichier.reconnaitre(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }));
		assertEquals(Optional.of("image/png"),
				TypeFichier.reconnaitre(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0 }));
		// Fichiers d'exemple (US-13).
		assertEquals(Optional.of("application/pdf"),
				TypeFichier.reconnaitre(exemple("facture-client-0042.pdf")));
		assertEquals(Optional.of("image/png"), TypeFichier.reconnaitre(exemple("ticket-fournitures.png")));
	}

	// CA2 : releve.xlsx (une archive ZIP), texte renommé en .pdf, fichier vide ou tronqué.
	@Test
	void autresFormatsRefuses() {
		assertEquals(Optional.empty(), TypeFichier.reconnaitre(new byte[] { 'P', 'K', 3, 4 }));
		assertEquals(Optional.empty(), TypeFichier.reconnaitre("bonjour".getBytes(StandardCharsets.UTF_8)));
		assertEquals(Optional.empty(), TypeFichier.reconnaitre(new byte[0]));
		assertEquals(Optional.empty(), TypeFichier.reconnaitre(null));
		assertEquals(Optional.empty(), TypeFichier.reconnaitre(new byte[] { (byte) 0x89, 'P', 'N' }));
	}

}
