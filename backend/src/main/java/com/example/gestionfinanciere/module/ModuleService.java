package com.example.gestionfinanciere.module;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-18 Modules à venir dans un dossier. L'appelant vérifie avant que la personne connectée est
// un collaborateur d'un cabinet ; ici, le dossier doit être celui de son cabinet (US-11 RG3).
@Service
@Loggable
public class ModuleService {

	private static final String EVENEMENT = "module.liste";
	private static final String CHAMP_DOSSIER = "dossierId";

	private final ModuleRepository modules;
	private final DossierRepository dossiers;

	public ModuleService(ModuleRepository modules, DossierRepository dossiers) {
		this.modules = modules;
		this.dossiers = dossiers;
	}

	// RG1 et RG3 : modules réglés en base ; vide si le dossier n'existe pas ou est d'un autre cabinet.
	@Transactional(readOnly = true)
	public Optional<List<VueModule>> lister(Compte collaborateur, Long dossierId) {
		if (dossiers.findByIdAndCabinetId(dossierId, collaborateur.getCabinet().getId()).isEmpty()) {
			EvenementMetier.de(EVENEMENT, "introuvable").avec(collaborateur.identifiantsPourLogs())
					.avec(CHAMP_DOSSIER, dossierId).warn();
			return Optional.empty();
		}
		List<VueModule> liste = modules.findAllByOrderByOrdreAscIdAsc().stream().map(VueModule::de).toList();
		EvenementMetier.de(EVENEMENT, "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec("nombre", liste.size()).info();
		return Optional.of(liste);
	}

}
