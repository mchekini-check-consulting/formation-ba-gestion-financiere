package com.example.gestionfinanciere.dossier;

import java.util.List;

// US-20 RG1 : comptables qui suivront le dossier (liste vide : plus aucun comptable).
public record DemandeAttribution(List<Long> comptableIds) {
}
