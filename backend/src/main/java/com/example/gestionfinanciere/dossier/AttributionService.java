package com.example.gestionfinanciere.dossier;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-20 Attribuer les dossiers aux comptables. L'appelant vérifie avant que la personne
// connectée est un collaborateur d'un cabinet.
@Service
@Loggable
public class AttributionService {

	private static final String CHAMP_DOSSIER = "dossierId";

	private final DossierRepository dossiers;
	private final AttributionDossierRepository attributions;
	private final CompteRepository comptes;
	private final Clock horloge;

	public AttributionService(DossierRepository dossiers, AttributionDossierRepository attributions,
			CompteRepository comptes, Clock horloge) {
		this.dossiers = dossiers;
		this.attributions = attributions;
		this.comptes = comptes;
		this.horloge = horloge;
	}

	// Comptables du cabinet, cochés s'ils suivent le dossier ; vide si le dossier n'est pas accessible.
	@Transactional(readOnly = true)
	public Optional<ComptablesDossier> consulter(Compte collaborateur, Long dossierId) {
		Optional<Dossier> dossier = AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId);
		if (dossier.isEmpty()) {
			EvenementMetier.de("dossier.comptables", "introuvable").avec(collaborateur.identifiantsPourLogs())
					.avec(CHAMP_DOSSIER, dossierId).warn();
			return Optional.empty();
		}
		return Optional.of(vue(collaborateur, dossier.get()));
	}

	// CA1 / RG1 : l'administrateur ou un associé choisit les comptables qui suivent le dossier.
	@Transactional
	public ResultatAttribution attribuer(Compte collaborateur, Long dossierId, DemandeAttribution demande) {
		ResultatAttribution resultat = attribuerSansJournal(collaborateur, dossierId, demande);
		EvenementMetier evenement = EvenementMetier.de("dossier.attribution", resultat.statut())
				.avec(collaborateur.identifiantsPourLogs()).avec(CHAMP_DOSSIER, dossierId);
		if (resultat.comptables() != null) {
			evenement.avec("nombre",
					resultat.comptables().comptables().stream().filter(ComptablesDossier.Comptable::attribue).count());
		}
		evenement.journaliser(resultat.statut() == ResultatAttribution.Statut.ATTRIBUE);
		return resultat;
	}

	private ResultatAttribution attribuerSansJournal(Compte collaborateur, Long dossierId,
			DemandeAttribution demande) {
		if (!AccesDossier.peutAttribuer(collaborateur)) {
			return ResultatAttribution.refus(ResultatAttribution.Statut.INTERDIT);
		}
		Optional<Dossier> dossier = AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId);
		if (dossier.isEmpty()) {
			return ResultatAttribution.refus(ResultatAttribution.Statut.INTROUVABLE);
		}
		if (demande == null || demande.comptableIds() == null
				|| demande.comptableIds().stream().anyMatch(Objects::isNull)) {
			return ResultatAttribution.refus(ResultatAttribution.Statut.INVALIDE);
		}
		Map<Long, Compte> parId = comptablesDuCabinet(collaborateur).stream()
				.collect(Collectors.toMap(Compte::getId, Function.identity()));
		Set<Long> choisis = new LinkedHashSet<>(demande.comptableIds());
		if (!parId.keySet().containsAll(choisis)) {
			return ResultatAttribution.refus(ResultatAttribution.Statut.INVALIDE);
		}
		attributions.supprimerPourDossier(dossierId);
		Instant maintenant = horloge.instant();
		attributions.saveAll(
				choisis.stream().map(id -> new AttributionDossier(dossier.get(), parId.get(id), maintenant)).toList());
		return ResultatAttribution.attribue(vue(collaborateur, dossier.get()));
	}

	private ComptablesDossier vue(Compte collaborateur, Dossier dossier) {
		Set<Long> attribues = attributions.findByDossierId(dossier.getId()).stream()
				.map(attribution -> attribution.getComptable().getId()).collect(Collectors.toSet());
		List<ComptablesDossier.Comptable> liste = comptablesDuCabinet(collaborateur).stream()
				.map(c -> new ComptablesDossier.Comptable(c.getId(), c.getNomAffiche(), attribues.contains(c.getId())))
				.toList();
		return new ComptablesDossier(liste, AccesDossier.peutAttribuer(collaborateur));
	}

	// Comptables du cabinet (actifs ou encore invités), par ordre alphabétique.
	private List<Compte> comptablesDuCabinet(Compte collaborateur) {
		return comptes.findByCabinetIdOrderByNomAfficheAsc(collaborateur.getCabinet().getId()).stream()
				.filter(c -> c.getRoleCabinet() == RoleCabinet.COMPTABLE).toList();
	}

}
