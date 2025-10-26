package ma.emsi.tp1lakehal.Llm;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.json.stream.JsonGenerator;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ma.emsi.tp1lakehal.Exception.RequeteException;

import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Gère la construction, la modification et l'extraction du JSON pour
 * les requêtes (conversationnelles) avec l'API Gemini.
 */
@Dependent
public class JsonUtilPourGemini implements Serializable {

    private String systemRole;

    // Pointeur pour ajouter un nouvel élément (Content Object) à la fin du tableau "contents"
    private final JsonPointer pointer = Json.createPointer(("/contents/-"));

    /** Requête JSON en cours de conversation (historique inclus). */
    private JsonObject requeteJson;
    /** Texte de la dernière requête JSON envoyée, formatée pour l'affichage. */
    private String texteRequeteJson;

    @Inject
    private LlmClient geminiClient;

    // --- Mutateurs ---

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public String getTexteRequeteJson() {
        return texteRequeteJson;
    }

    // --- Logique Principale d'Envoi ---

    /**
     * Envoie une requête à l'API de Gemini, gère l'historique de conversation et
     * traite la réponse.
     * @param question question posée par l'utilisateur.
     * @return un objet LlmInteraction contenant les textes de requête/réponse et la réponse extraite.
     * @throws RequeteException si la requête est rejetée par l'API (status != 200).
     * @throws IllegalArgumentException si la question est nulle ou vide.
     */
    public LlmInteraction envoyerRequete(String question) throws RequeteException {
        // 1. Vérification de l'input
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("La question de l'utilisateur ne peut pas être nulle ou vide.");
        }

        String requestBody;
        if (this.requeteJson == null) {
            // Initialisation : Crée la requête avec le rôle système et la première question.
            requestBody = creerRequeteJson(this.systemRole, question);
        } else {
            // Continuation : Ajoute la question à l'historique existant.
            requestBody = ajouteQuestionDansJsonRequete(question);
        }

        // Met à jour la version formatée de la requête (pour l'affichage)
        this.texteRequeteJson = prettyPrinting(requeteJson);

        // Entité JAX-RS à envoyer (le corps JSON)
        Entity<String> entity = Entity.entity(requestBody, MediaType.APPLICATION_JSON_TYPE);

        // 2. Envoi de la requête
        try (Response response = geminiClient.envoyerRequete(entity)) {

            String texteReponseJson = response.readEntity(String.class);

            if (response.getStatus() == 200) {
                // Succès : Extrait la réponse et met à jour l'historique
                String reponseExtraite = extractReponse(texteReponseJson);
                return new LlmInteraction(this.texteRequeteJson, texteReponseJson, reponseExtraite);
            } else {
                // Échec : L'API a retourné une erreur
                JsonObject objet = Json.createReader(new StringReader(requestBody)).readObject();
                throw new RequeteException(
                        response.getStatus() + " : " + response.getStatusInfo(),
                        "Erreur API. Requête JSON envoyée:\n" + prettyPrinting(objet) + "\nRéponse JSON de l'API:\n" + texteReponseJson
                );
            }
        }
    }

    // --- Construction JSON ---

    /**
     * Crée la structure JSON initiale pour la première requête (inclut l'instruction système).
     * Structure correcte pour Gemini API:
     * {
     * "systemInstruction": {
     * "parts": [{"text": "..."}]
     * },
     * "contents": [...]
     * }
     */
    private String creerRequeteJson(String systemRole, String question) {
        JsonObjectBuilder rootBuilder = Json.createObjectBuilder();

        // Ajout de l'instruction système si elle existe (structure CORRECTE pour Gemini)
        if (systemRole != null && !systemRole.trim().isEmpty()) {
            JsonObject systemInstructionPart = Json.createObjectBuilder()
                    .add("text", systemRole)
                    .build();

            JsonObject systemInstruction = Json.createObjectBuilder()
                    .add("parts", Json.createArrayBuilder()
                            .add(systemInstructionPart)
                            .build())
                    .build();

            rootBuilder.add("systemInstruction", systemInstruction);
        }

        // Crée le 'Part Object' pour la question utilisateur
        JsonObject userPart = Json.createObjectBuilder()
                .add("text", question)
                .build();

        // Crée le 'Content Object' (rôle + parts)
        JsonObject userContent = Json.createObjectBuilder()
                .add("role", "user") // CORRECTION : Rétablir le rôle "user" pour le premier message dans "contents"
                .add("parts", Json.createArrayBuilder()
                        .add(userPart)
                        .build())
                .build();

        // Ajoute le tableau 'contents' contenant le premier message utilisateur
        JsonObject rootJson = rootBuilder.add("contents", Json.createArrayBuilder()
                        .add(userContent)
                        .build())
                .build();

        this.requeteJson = rootJson;
        return rootJson.toString();
    }

    /**
     * Ajoute le nouveau message de l'utilisateur à la conversation JSON existante.
     */
    private String ajouteQuestionDansJsonRequete(String nouvelleQuestion) {
        // 1. Crée le 'Part Object' : {"text": "nouvelleQuestion"}
        JsonObject newPartObject = Json.createObjectBuilder()
                .add("text", nouvelleQuestion)
                .build();

        // 2. Crée le 'Content Object' : {"role": "user", "parts": [Part Object]}
        JsonObject newContentObject = Json.createObjectBuilder()
                .add("role", "user")
                .add("parts", Json.createArrayBuilder()
                        .add(newPartObject)
                        .build())
                .build();

        // 3. Ajoute ce nouveau Content Object à la fin du tableau 'contents'
        this.requeteJson = this.pointer.add(this.requeteJson, newContentObject);

        return this.requeteJson.toString();
    }

    // --- Extraction et Formatage ---

    /**
     * Extrait la réponse du modèle et ajoute la réponse (role: model) à l'historique.
     */
    private String extractReponse(String json) throws RequeteException {
        try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            JsonObject jsonObject = jsonReader.readObject();

            // Vérifie l'existence des candidats
            if (!jsonObject.containsKey("candidates") || jsonObject.getJsonArray("candidates").isEmpty()) {
                throw new RequeteException("La réponse de l'API est vide. Contenu bloqué ou aucune réponse générée.", json);
            }

            // Récupère l'objet 'content' de la réponse du modèle
            JsonObject modelContent = jsonObject
                    .getJsonArray("candidates")
                    .getJsonObject(0)
                    .getJsonObject("content");

            // Ajoute l'objet JSON de la réponse du modèle au JSON de la prochaine requête
            this.requeteJson = this.pointer.add(this.requeteJson, modelContent);

            // Extrait seulement le texte de la réponse
            return modelContent.getJsonArray("parts").getJsonObject(0).getString("text");
        } catch (JsonException | IndexOutOfBoundsException e) {
            throw new RequeteException("Erreur lors de l'extraction de la réponse JSON de Gemini.", json);
        }
    }

    /**
     * Retourne le texte formaté du document JSON pour un affichage plus agréable (pretty printing).
     */
    private String prettyPrinting(JsonObject jsonObject) {
        if (jsonObject == null) return "{}";
        Map<String, Boolean> config = new HashMap<>();
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(config);
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = writerFactory.createWriter(stringWriter)) {
            jsonWriter.write(jsonObject);
        }
        return stringWriter.toString();
    }
}
