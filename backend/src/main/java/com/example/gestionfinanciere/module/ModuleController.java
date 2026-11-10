package com.example.gestionfinanciere.module;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-18 Modules à venir d'un dossier, exposés par le gateway sous /backend/dossiers/{id}/modules ;
// mêmes droits que le dossier (US-11 RG3), session obligatoire (US-07) :
//   GET /dossiers/{id}/modules  modules proposés (RG1), réglés côté serveur (RG3)
@RestController
@RequestMapping("/dossiers/{dossierId}/modules")
public class ModuleController {

	private final ModuleService modules;
	private final SessionService sessions;

	public ModuleController(ModuleService modules, SessionService sessions) {
		this.modules = modules;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> lister(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		return modules.lister(session.compte(), numero.get()).<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElseGet(DossierController::introuvable);
	}

}
