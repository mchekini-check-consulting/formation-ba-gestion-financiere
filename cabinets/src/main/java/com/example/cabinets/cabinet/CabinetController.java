package com.example.cabinets.cabinet;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// CRUD des comptes cabinets :
//   GET /cabinets, GET /cabinets/{id}, POST /cabinets, PUT /cabinets/{id}, DELETE /cabinets/{id}
// Exposé par le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/cabinets/cabinets
@RestController
@RequestMapping("/cabinets")
public class CabinetController {

	private final CabinetService cabinets;

	public CabinetController(CabinetService cabinets) {
		this.cabinets = cabinets;
	}

	@GetMapping
	public List<Cabinet> lister() {
		return cabinets.lister();
	}

	@GetMapping("/{id}")
	public Cabinet lire(@PathVariable Long id) {
		return cabinets.lire(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Cabinet creer(@RequestBody CabinetRequest requete) {
		return cabinets.creer(requete);
	}

	@PutMapping("/{id}")
	public Cabinet modifier(@PathVariable Long id, @RequestBody CabinetRequest requete) {
		return cabinets.modifier(id, requete);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void supprimer(@PathVariable Long id) {
		cabinets.supprimer(id);
	}

}
