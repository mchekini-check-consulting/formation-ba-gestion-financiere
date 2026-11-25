import java.io.IOException;

/**
 * Faux programme Tesseract pour TesseractOcrTest, lancé par « java FauxTesseract.java <mode> ».
 * texte : recopie l'image reçue (texte) ; erreur : code de sortie 1 ; lent : ne répond pas.
 */
public class FauxTesseract {

	public static void main(String[] args) throws IOException, InterruptedException {
		switch (args[0]) {
			case "texte" -> System.out.write(System.in.readAllBytes());
			case "erreur" -> System.exit(1);
			default -> Thread.sleep(60_000);
		}
		System.out.flush();
	}

}
