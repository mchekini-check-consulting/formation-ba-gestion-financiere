package com.example.cabinets.cabinet;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.cabinets.logging.Loggable;

// Règles métier des comptes cabinets : champs obligatoires, SIREN à 9 chiffres et unique.
@Service
@Loggable
public class CabinetService {

	private static final Pattern SIREN = Pattern.compile("\\d{9}");
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
	private static final String SIREN_DEJA_PRIS = "un cabinet existe déjà avec ce SIREN";

	private final CabinetRepository cabinets;

	public CabinetService(CabinetRepository cabinets) {
		this.cabinets = cabinets;
	}

	public List<Cabinet> lister() {
		return cabinets.findAll(Sort.by("id"));
	}

	public Cabinet lire(Long id) {
		return cabinets.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cabinet introuvable"));
	}

	@Transactional
	public Cabinet creer(CabinetRequest requete) {
		Valeurs v = valider(requete);
		if (cabinets.existsBySiren(v.siren())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, SIREN_DEJA_PRIS);
		}
		return cabinets.save(new Cabinet(v.nom(), v.siren(), v.email(), v.nombreDossiers()));
	}

	@Transactional
	public Cabinet modifier(Long id, CabinetRequest requete) {
		Cabinet cabinet = lire(id);
		Valeurs v = valider(requete);
		if (cabinets.existsBySirenAndIdNot(v.siren(), id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, SIREN_DEJA_PRIS);
		}
		cabinet.modifier(v.nom(), v.siren(), v.email(), v.nombreDossiers());
		return cabinets.save(cabinet);
	}

	@Transactional
	public void supprimer(Long id) {
		cabinets.delete(lire(id));
	}

	private record Valeurs(String nom, String siren, String email, int nombreDossiers) {
	}

	private static Valeurs valider(CabinetRequest requete) {
		String nom = nettoyer(requete.nom());
		if (nom.isEmpty() || nom.length() > 200) {
			throw invalide("nom obligatoire (200 caractères maximum)");
		}
		String siren = nettoyer(requete.siren()).replace(" ", "");
		if (!SIREN.matcher(siren).matches()) {
			throw invalide("le SIREN doit contenir 9 chiffres");
		}
		String email = nettoyer(requete.email()).toLowerCase(Locale.ROOT);
		if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
			throw invalide("email invalide");
		}
		int nombreDossiers = requete.nombreDossiers() == null ? 0 : requete.nombreDossiers();
		if (nombreDossiers < 0) {
			throw invalide("le nombre de dossiers ne peut pas être négatif");
		}
		return new Valeurs(nom, siren, email, nombreDossiers);
	}

	private static String nettoyer(String valeur) {
		return valeur == null ? "" : valeur.trim();
	}

	private static ResponseStatusException invalide(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
