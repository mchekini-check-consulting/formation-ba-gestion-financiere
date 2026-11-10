package com.example.gestionfinanciere.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mockito.ArgumentCaptor;

// Lit dans les emails envoyés (EnvoiEmail simulé par Mockito) le code ou le lien reçu, comme le
// ferait l'utilisateur dans sa boîte : le backend ne renvoie jamais ni code ni lien à l'écran.
public final class EmailsRecus {

	private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
	private static final Pattern JETON = Pattern.compile("jeton=(\\S+)");

	private EmailsRecus() {
	}

	// Corps de tous les emails envoyés à ce destinataire, du plus ancien au plus récent.
	public static List<String> corps(EnvoiEmail envoi, String destinataire) {
		ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
		verify(envoi, atLeastOnce()).envoyer(eq(destinataire), anyString(), corps.capture());
		return corps.getAllValues();
	}

	public static String dernierCorps(EnvoiEmail envoi, String destinataire) {
		List<String> tous = corps(envoi, destinataire);
		return tous.get(tous.size() - 1);
	}

	// Code à 6 chiffres du dernier email envoyé à ce destinataire.
	public static String dernierCode(EnvoiEmail envoi, String destinataire) {
		return code(dernierCorps(envoi, destinataire));
	}

	// Jeton du lien (…?jeton=…) du dernier email envoyé à ce destinataire.
	public static String dernierJeton(EnvoiEmail envoi, String destinataire) {
		return extraire(JETON, dernierCorps(envoi, destinataire));
	}

	// Code à 6 chiffres du dernier email envoyé, quel que soit le destinataire.
	public static String dernierCode(EnvoiEmail envoi) {
		return code(dernierCorps(envoi));
	}

	// Jeton du lien du dernier email envoyé, quel que soit le destinataire.
	public static String dernierJeton(EnvoiEmail envoi) {
		return extraire(JETON, dernierCorps(envoi));
	}

	private static String dernierCorps(EnvoiEmail envoi) {
		ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
		verify(envoi, atLeastOnce()).envoyer(anyString(), anyString(), corps.capture());
		List<String> tous = corps.getAllValues();
		return tous.get(tous.size() - 1);
	}

	public static String code(String corps) {
		return extraire(CODE, corps);
	}

	private static String extraire(Pattern motif, String corps) {
		Matcher trouve = motif.matcher(corps);
		if (!trouve.find()) {
			throw new AssertionError("rien trouvé dans l'email : " + corps);
		}
		return trouve.group(1);
	}

}
