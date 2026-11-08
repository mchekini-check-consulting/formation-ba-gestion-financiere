package com.example.gestionfinanciere.compte;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Jetons secrets (défi du code de connexion, lien de réinitialisation, session) : tirés au hasard,
// donnés une seule fois à l'utilisateur ; le serveur n'en garde que l'empreinte SHA-256.
public final class Jetons {

	private static final SecureRandom ALEA = new SecureRandom();

	private Jetons() {
	}

	public static String nouveau() {
		byte[] octets = new byte[32];
		ALEA.nextBytes(octets);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
	}

	public static String empreinte(String jeton) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(jeton.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}

	// US-04 RG1 : code à 6 chiffres.
	public static String codeSixChiffres() {
		return String.format("%06d", ALEA.nextInt(1_000_000));
	}

}
