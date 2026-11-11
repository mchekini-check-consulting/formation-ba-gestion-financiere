package com.example.gestionfinanciere.module;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-18 Modules à venir, affichés dans le menu de gauche ; exposés par le gateway sous
// /backend/modules ; réservés aux collaborateurs d'un cabinet, session obligatoire (US-07) :
//   GET /modules  modules proposés (RG1), réglés côté serveur (RG3)
@RestController
@RequestMapping("/modules")
public class ModuleController {

	private final ModuleService modules;
	private final SessionService sessions;

	public ModuleController(ModuleService modules, SessionService sessions) {
		this.modules = modules;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> lister(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		return ResponseEntity.ok(modules.lister(session.compte()));
	}

}
