package com.example.gestionfinanciere.facture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.gestionfinanciere.compte.ErreurResponse;

// US-15 RG1 : fichier refusé par le serveur avant même d'arriver au contrôleur (au-delà de la
// limite spring.servlet.multipart de application.yaml) -> même message que les autres trop gros.
@RestControllerAdvice
public class FichierTropVolumineuxHandler {

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErreurResponse> tropVolumineux() {
		return ResponseEntity.badRequest().body(new ErreurResponse(EspaceClientController.MESSAGE_TAILLE));
	}

}
