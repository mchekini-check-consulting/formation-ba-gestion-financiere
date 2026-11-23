package com.example.gestionfinanciere.facture.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.sun.net.httpserver.HttpServer;

/**
 * US-53 : OCR par Claude, sans appeler le vrai service : un faux serveur local répond à la place
 * de l'API Anthropic et garde la dernière demande reçue.
 */
class ClaudeOcrTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G' };

	private HttpServer serveur;
	private final AtomicReference<String> demande = new AtomicReference<>();
	private final AtomicReference<String> beta = new AtomicReference<>();
	private volatile int code = 200;
	private volatile String reponse;

	@BeforeEach
	void demarrer() throws IOException {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/", echange -> {
			demande.set(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			beta.set(echange.getRequestHeaders().getFirst("anthropic-beta"));
			byte[] corps = reponse.getBytes(StandardCharsets.UTF_8);
			echange.getResponseHeaders().add("Content-Type", "application/json");
			// Pas de nouvel essai automatique du client sur les erreurs simulées.
			echange.getResponseHeaders().add("x-should-retry", "false");
			echange.sendResponseHeaders(code, corps.length);
			echange.getResponseBody().write(corps);
			echange.close();
		});
		serveur.start();
	}

	@AfterEach
	void arreter() {
		serveur.stop(0);
	}

	private MoteurOcr ocr() {
		return new LectureConfiguration().moteurOcr("cle-de-test", "claude-opus-5-5",
				"http://127.0.0.1:" + serveur.getAddress().getPort(), Duration.ofSeconds(10));
	}

	private static String message(String stopReason, String texte) {
		String echappe = texte.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return """
				{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
				 "content":[{"type":"text","text":"%s"}],"stop_reason":"%s","stop_sequence":null,
				 "usage":{"input_tokens":10,"output_tokens":5}}""".formatted(echappe, stopReason);
	}

	// CA1 : le texte recopié par Claude est renvoyé ; la demande porte l'image, le modèle et le secours.
	@Test
	void texteLu() {
		reponse = message("end_turn", "EDF\nTotal TTC 186,40 €\n");

		assertEquals(Optional.of("EDF\nTotal TTC 186,40 €"), ocr().lire(PNG, "image/png"));
		String corps = demande.get();
		assertTrue(corps.contains("\"model\":\"claude-opus-5-5\""));
		assertTrue(corps.contains("\"media_type\":\"image/png\""));
		assertTrue(corps.contains("\"data\":\"iVBORw==\""));
		assertTrue(corps.contains("\"fallbacks\":\"default\""));
		assertTrue(corps.contains("\"effort\":\"low\""));
		assertTrue(beta.get().contains("server-side-fallback-2026-07-01"));
	}

	// CA4 : rien de lisible, refus ou erreur du service : vide (-> « Lecture échouée »), jamais d'exception.
	@Test
	void rienDeLu() {
		reponse = message("end_turn", ClaudeOcr.AUCUN_TEXTE);
		assertEquals(Optional.empty(), ocr().lire(PNG, "image/jpeg"));

		reponse = message("end_turn", "  ");
		assertEquals(Optional.empty(), ocr().lire(PNG, "image/png"));

		reponse = message("refusal", "");
		assertEquals(Optional.empty(), ocr().lire(PNG, "image/png"));

		code = 400;
		reponse = """
				{"type":"error","error":{"type":"invalid_request_error","message":"image invalide"}}""";
		assertEquals(Optional.empty(), ocr().lire(PNG, "image/png"));
	}

	// Types que Claude ne lit pas (HEIC, TIFF...) ou image vide : pas d'appel, vide.
	@Test
	void imageNonPriseEnCharge() {
		reponse = message("end_turn", "ne doit pas servir");
		assertEquals(Optional.empty(), ocr().lire(PNG, "image/heic"));
		assertEquals(Optional.empty(), ocr().lire(PNG, null));
		assertEquals(Optional.empty(), ocr().lire(new byte[0], "image/png"));
		assertEquals(null, demande.get());
	}

	// Photo trop lourde pour l'API : réduite et convertie en JPEG ; illisible : abandonnée.
	@Test
	void photoTropLourde() throws IOException {
		BufferedImage bruit = new BufferedImage(2600, 2600, BufferedImage.TYPE_INT_RGB);
		java.util.Random hasard = new java.util.Random(53);
		for (int x = 0; x < bruit.getWidth(); x++) {
			for (int y = 0; y < bruit.getHeight(); y++) {
				bruit.setRGB(x, y, hasard.nextInt());
			}
		}
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(bruit, "png", png);
		assertTrue(png.size() > ClaudeOcr.TAILLE_MAX);

		Optional<ClaudeOcr.ImageAEnvoyer> envoi = ClaudeOcr.preparer(png.toByteArray(), "image/png");

		assertTrue(envoi.isPresent());
		assertEquals(BetaBase64ImageSource.MediaType.IMAGE_JPEG, envoi.get().type());
		byte[] jpeg = java.util.Base64.getDecoder().decode(envoi.get().base64());
		assertTrue(jpeg.length <= ClaudeOcr.TAILLE_MAX);
		BufferedImage reduite = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
		assertEquals(ClaudeOcr.COTE_MAX, reduite.getWidth());

		assertEquals(Optional.empty(), ClaudeOcr.preparer(new byte[ClaudeOcr.TAILLE_MAX + 1], "image/png"));
	}

	// Sans clé de l'API (poste local, clé pas encore injectée) : OCR indisponible, toujours vide.
	@Test
	void sansCle() {
		MoteurOcr ocr = new LectureConfiguration().moteurOcr("", "claude-opus-5-5", "", Duration.ofSeconds(10));

		assertInstanceOf(OcrIndisponible.class, ocr);
		assertEquals(Optional.empty(), ocr.lire(PNG, "image/png"));
		// Clé fournie, adresse par défaut (API Anthropic) : Claude, sans appel à la création.
		assertInstanceOf(ClaudeOcr.class,
				new LectureConfiguration().moteurOcr("cle", "claude-opus-5-5", "", Duration.ofSeconds(10)));
	}

}
