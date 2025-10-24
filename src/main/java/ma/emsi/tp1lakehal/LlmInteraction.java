package ma.emsi.tp1lakehal;

/**
 * Représente une interaction complète avec l'API LLM (Large Language Model).
 * Contient la requête JSON envoyée, la réponse JSON brute reçue et le texte simple extrait de la réponse.
 */
public class LlmInteraction {
    private final String requestJson;
    private final String responseJson;
    private final String extractedText;

    /**
     * Constructeur requis par JsonUtilPourGemini.envoyerRequete().
     *
     * @param requestJson Le corps JSON formaté de la requête envoyée.
     * @param responseJson Le corps JSON brut de la réponse reçue de l'API.
     * @param extractedText Le texte de la réponse, extrait du JSON.
     */
    public LlmInteraction(String requestJson, String responseJson, String extractedText) {
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.extractedText = extractedText;
    }

    /**
     * @return Le texte de la réponse, extrait du JSON.
     */
    public String reponseExtraite() {
        return extractedText;
    }

    /**
     * @return Le corps JSON formaté de la requête envoyée.
     */
    public String questionJson() {
        return requestJson;
    }

    /**
     * @return Le corps JSON brut de la réponse reçue de l'API.
     */
    public String reponseJson() {
        return responseJson;
    }
}