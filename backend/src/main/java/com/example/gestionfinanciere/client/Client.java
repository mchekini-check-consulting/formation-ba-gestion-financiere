package com.example.gestionfinanciere.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "client")
public class Client {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nom_client", nullable = false)
	private String nomClient;

	@Column(name = "nom_entreprise", nullable = false)
	private String nomEntreprise;

	@Column(name = "nombre_donnees_connectees", nullable = false)
	private int nombreDonneesConnectees;

	protected Client() {
	}

	public Client(String nomClient, String nomEntreprise, int nombreDonneesConnectees) {
		this.nomClient = nomClient;
		this.nomEntreprise = nomEntreprise;
		this.nombreDonneesConnectees = nombreDonneesConnectees;
	}

	public Long getId() {
		return id;
	}

	public String getNomClient() {
		return nomClient;
	}

	public String getNomEntreprise() {
		return nomEntreprise;
	}

	public int getNombreDonneesConnectees() {
		return nombreDonneesConnectees;
	}

}
