package ma.emsi.tp1lakehal.Llm;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.Serializable;

/**
 * Gère l'interface avec l'API de Gemini (Modèle gemini-pro:generateContent).
 * Utilise la variable d'environnement 'GEMINI' pour l'authentification.
 *
 * De portée dependent pour réinitialiser la conversation à chaque utilisation.
 */
@Dependent
public class LlmClient implements Serializable {
    // Nom de la variable d'environnement CONFIRMÉ : "GEMINii"
    private static final String API_KEY_ENV_VAR = "GEMINI";

    // URL de l'API Gemini pour la génération de contenu (le modèle 'gemini-pro')
    private static final String GEMINI_PRO_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // Clé pour l'API du LLM
    private final String key;
    // Client REST JAX-RS
    private Client clientRest;
    // Représente le endpoint REST configuré (URL + clé)
    private final WebTarget target;

    public LlmClient() {
        // 1. Récupère la clé secrète en utilisant le nom de variable confirmé : "GEMINI"
        this.key = System.getenv(API_KEY_ENV_VAR);

        if (this.key == null || this.key.trim().isEmpty()) {
            throw new IllegalStateException("La variable d'environnement '" + API_KEY_ENV_VAR + "' (clé API) n'est pas définie ou est vide.");
        }

        // 2. Client REST pour envoyer des requêtes
        this.clientRest = ClientBuilder.newClient();

        // 3. Endpoint REST : ajoute la clé API comme paramètre de requête.
        this.target = clientRest.target(GEMINI_PRO_URL)
                .queryParam("key", this.key);
    }

    /**
     * Envoie une requête POST à l'API de Gemini pour générer du contenu.
     * @param requestEntity le corps de la requête (en JSON).
     * @return réponse REST de l'API (corps en JSON).
     */
    public Response envoyerRequete(Entity requestEntity) {
        Invocation.Builder request = target.request(MediaType.APPLICATION_JSON_TYPE);
        return request.post(requestEntity);
    }

    /**
     * Ferme le client REST pour libérer les ressources.
     */
    public void closeClient() {
        if (this.clientRest != null) {
            this.clientRest.close();
        }
    }
}