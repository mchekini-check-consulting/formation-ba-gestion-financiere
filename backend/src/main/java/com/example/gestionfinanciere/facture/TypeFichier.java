package com.example.gestionfinanciere.facture;

import java.util.Optional;

/**
 * US-15 RG1 : PDF, JPG ou PNG uniquement. Le type est reconnu d'après le contenu du fichier
 * (ses premiers octets), jamais d'après son nom ni le type annoncé par le navigateur.
 */
public final class TypeFichier {

	public static final String PDF = "application/pdf";
	public static final String JPEG = "image/jpeg";
	public static final String PNG = "image/png";

	private static final byte[] DEBUT_PDF = { '%', 'P', 'D', 'F', '-' };
	private static final byte[] DEBUT_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] DEBUT_PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private TypeFichier() {
	}

	// Type reconnu du fichier ; vide s'il n'est ni PDF, ni JPG, ni PNG.
	public static Optional<String> reconnaitre(byte[] contenu) {
		if (commencePar(contenu, DEBUT_PDF)) {
			return Optional.of(PDF);
		}
		if (commencePar(contenu, DEBUT_JPEG)) {
			return Optional.of(JPEG);
		}
		if (commencePar(contenu, DEBUT_PNG)) {
			return Optional.of(PNG);
		}
		return Optional.empty();
	}

	private static boolean commencePar(byte[] contenu, byte[] debut) {
		if (contenu == null || contenu.length < debut.length) {
			return false;
		}
		for (int i = 0; i < debut.length; i++) {
			if (contenu[i] != debut[i]) {
				return false;
			}
		}
		return true;
	}

}
