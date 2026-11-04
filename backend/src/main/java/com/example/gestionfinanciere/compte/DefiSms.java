package com.example.gestionfinanciere.compte;

// Code SMS envoyé : défi à rappeler avec le code, numéro masqué ;
// codeDeTest renseigné uniquement en mode test (aucun opérateur SMS branché).
public record DefiSms(String defi, String telephoneMasque, String codeDeTest) {
}
