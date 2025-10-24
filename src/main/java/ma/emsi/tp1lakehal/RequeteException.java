package ma.emsi.tp1lakehal; // Utilisez votre package

/**
 * Exception personnalisée pour signaler une erreur lors de l'interaction avec le LLM
 * (ex: erreur HTTP, problème de parsing JSON).
 */
public class RequeteException extends Exception {

    /**
     * Constructeur avec message d'erreur.
     */
    public RequeteException(String message) {
        super(message);
    }

    /**
     * Constructeur avec un message et une chaîne de contexte (utile pour stocker le JSON brut en cas d'erreur).
     */
    public RequeteException(String message, String context) {
        // Le message d'erreur affichera un résumé + le contexte (JSON)
        super(message + "\n--- Contexte d'erreur ---\n" + context);
    }

    /**
     * Constructeur avec un message et la cause (utile pour envelopper une autre exception).
     */
    public RequeteException(String message, Throwable cause) {
        super(message, cause);
    }
}