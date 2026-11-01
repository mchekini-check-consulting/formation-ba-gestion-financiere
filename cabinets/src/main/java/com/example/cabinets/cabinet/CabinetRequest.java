package com.example.cabinets.cabinet;

// Corps de POST /cabinets et PUT /cabinets/{id}
public record CabinetRequest(String nom, String siren, String email, Integer nombreDossiers) {
}
