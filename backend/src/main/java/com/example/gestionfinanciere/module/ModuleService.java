package com.example.gestionfinanciere.module;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-18 Modules à venir, affichés dans le menu de gauche. L'appelant vérifie avant que la
// personne connectée est un collaborateur d'un cabinet.
@Service
@Loggable
public class ModuleService {

	private final ModuleRepository modules;

	public ModuleService(ModuleRepository modules) {
		this.modules = modules;
	}

	// RG1 et RG3 : modules réglés en base, dans l'ordre réglé en base.
	@Transactional(readOnly = true)
	public List<VueModule> lister(Compte collaborateur) {
		List<VueModule> liste = modules.findAllByOrderByOrdreAscIdAsc().stream().map(VueModule::de).toList();
		EvenementMetier.de("module.liste", "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec("nombre", liste.size()).info();
		return liste;
	}

}
