package com.example.gestionfinanciere.compte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

// Horloge de test qu'on avance à la main (blocage 15 min, code 5 min, lien 1 h, session 30 min).
public class HorlogeReglable extends Clock {

	private Instant maintenant;

	public HorlogeReglable(Instant depart) {
		this.maintenant = depart;
	}

	public void avancer(Duration duree) {
		maintenant = maintenant.plus(duree);
	}

	@Override
	public Instant instant() {
		return maintenant;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
