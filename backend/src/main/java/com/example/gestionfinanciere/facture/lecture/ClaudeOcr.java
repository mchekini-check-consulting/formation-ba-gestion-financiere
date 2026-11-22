package com.example.gestionfinanciere.facture.lecture;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

/**
 * US-53 : OCR par Claude (API Anthropic). L'image est envoyée à Claude, qui en recopie le texte ;
 * l'analyse des champs (fournisseur, montants...) reste faite par AnalyseurFacture. Type d'image
 * non pris en charge, refus, erreur ou délai dépassé : vide, la facture passe en « Lecture échouée ».
 * Jamais le contenu de l'image ni le texte lu dans les logs.
 */
public class ClaudeOcr implements MoteurOcr {

	private static final Logger LOG = LoggerFactory.getLogger(ClaudeOcr.class);

	// Réponse convenue quand l'image ne contient aucun texte lisible.
	static final String AUCUN_TEXTE = "AUCUN_TEXTE";
	// L'API refuse une image de plus de 5 Mo une fois encodée (base64 : +33 %) : marge de sécurité.
	static final int TAILLE_MAX = 3_500_000;
	// Au-delà, Claude réduit lui-même l'image : on la réduit avant l'envoi quand elle est trop lourde.
	static final int COTE_MAX = 2000;
	private static final long JETONS_MAX = 16_000L;

	static final String CONSIGNE = """
			Tu reçois la photo ou le scan d'une facture. Recopie fidèlement tout le texte visible, \
			dans l'ordre de lecture, une ligne de la facture par ligne. Garde les montants, dates, \
			numéros et pourcentages exactement comme ils sont écrits. N'ajoute ni commentaire, ni \
			titre, ni mise en forme Markdown. Si l'image ne contient aucun texte lisible, réponds \
			uniquement : %s""".formatted(AUCUN_TEXTE);

	private static final Map<String, BetaBase64ImageSource.MediaType> TYPES = Map.of(
			"image/png", BetaBase64ImageSource.MediaType.IMAGE_PNG,
			"image/jpeg", BetaBase64ImageSource.MediaType.IMAGE_JPEG,
			"image/jpg", BetaBase64ImageSource.MediaType.IMAGE_JPEG,
			"image/gif", BetaBase64ImageSource.MediaType.IMAGE_GIF,
			"image/webp", BetaBase64ImageSource.MediaType.IMAGE_WEBP);

	private final AnthropicClient client;
	private final String modele;

	public ClaudeOcr(AnthropicClient client, String modele) {
		this.client = client;
		this.modele = modele;
	}

	@Override
	public Optional<String> lire(byte[] image, String typeImage) {
		Optional<ImageAEnvoyer> envoi = preparer(image, typeImage);
		if (envoi.isEmpty()) {
			LOG.warn("OCR : image non prise en charge par Claude");
			return Optional.empty();
		}
		try {
			BetaMessage reponse = client.beta().messages().create(demande(envoi.get()));
			return texte(reponse);
		} catch (AnthropicException e) {
			LOG.warn("OCR : appel à Claude en échec ({})", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	MessageCreateParams demande(ImageAEnvoyer envoi) {
		BetaImageBlockParam image = BetaImageBlockParam.builder()
				.source(BetaBase64ImageSource.builder()
						.mediaType(envoi.type())
						.data(Base64.getEncoder().encodeToString(envoi.contenu()))
						.build())
				.build();
		return MessageCreateParams.builder()
				.model(modele)
				.maxTokens(JETONS_MAX)
				// Recopier un texte est simple : peu de réflexion, réponse plus rapide et moins chère.
				.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
				// En cas de refus par les filtres de sécurité, l'API rejoue la demande sur un autre modèle.
				.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
				.fallbacksDefault()
				.system(CONSIGNE)
				.addUserMessageOfContentBlockParams(List.of(
						BetaContentBlockParam.ofImage(image),
						BetaContentBlockParam.ofText("Recopie le texte de cette facture.")))
				.build();
	}

	static Optional<String> texte(BetaMessage reponse) {
		if (reponse.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
			LOG.warn("OCR : lecture refusée par Claude");
			return Optional.empty();
		}
		String texte = reponse.content().stream()
				.map(BetaContentBlock::text)
				.flatMap(Optional::stream)
				.map(bloc -> bloc.text())
				.collect(Collectors.joining("\n"))
				.strip();
		return texte.isEmpty() || AUCUN_TEXTE.equals(texte) ? Optional.empty() : Optional.of(texte);
	}

	record ImageAEnvoyer(byte[] contenu, BetaBase64ImageSource.MediaType type) {
	}

	// Type accepté par Claude ; image trop lourde (photo de téléphone) : réduite et convertie en JPEG.
	static Optional<ImageAEnvoyer> preparer(byte[] image, String typeImage) {
		BetaBase64ImageSource.MediaType type = typeImage == null ? null : TYPES.get(typeImage.toLowerCase());
		if (image == null || image.length == 0 || type == null) {
			return Optional.empty();
		}
		if (image.length <= TAILLE_MAX) {
			return Optional.of(new ImageAEnvoyer(image, type));
		}
		return reduire(image).map(jpeg -> new ImageAEnvoyer(jpeg, BetaBase64ImageSource.MediaType.IMAGE_JPEG));
	}

	private static Optional<byte[]> reduire(byte[] image) {
		try {
			BufferedImage originale = ImageIO.read(new ByteArrayInputStream(image));
			if (originale == null) {
				return Optional.empty();
			}
			double echelle = Math.min(1.0, (double) COTE_MAX / Math.max(originale.getWidth(), originale.getHeight()));
			int largeur = Math.max(1, (int) Math.round(originale.getWidth() * echelle));
			int hauteur = Math.max(1, (int) Math.round(originale.getHeight() * echelle));
			BufferedImage reduite = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
			reduite.getGraphics().drawImage(originale.getScaledInstance(largeur, hauteur, Image.SCALE_SMOOTH), 0, 0,
					null);
			ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
			ImageIO.write(reduite, "jpg", jpeg);
			byte[] resultat = jpeg.toByteArray();
			return resultat.length <= TAILLE_MAX ? Optional.of(resultat) : Optional.empty();
		} catch (IOException e) {
			return Optional.empty();
		}
	}

}
