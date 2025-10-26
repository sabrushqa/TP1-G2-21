package ma.emsi.tp1lakehal.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.tp1lakehal.Llm.JsonUtilPourGemini;
import ma.emsi.tp1lakehal.Llm.LlmInteraction;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation qui dure pendant plusieurs requêtes HTTP.
 * La portée view nécessite l'implémentation de Serializable (le backing bean peut être mis en mémoire secondaire).
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    // =================================================================================
    // CONSTANTES - RÔLES SYSTÈME COMPLETS
    // =================================================================================

    private static final String ROLE_ASSISTANT_COMPLET = """
            You are a helpful assistant. You help the user to find the information they need.
            If the user type a question, you answer it.
            """;

    private static final String ROLE_TRADUCTEUR_COMPLET = """
            You are an interpreter. You translate from English to French and from French to English.
            If the user type a French text, you translate it into English.
            If the user type an English text, you translate it into French.
            If the text contains only one to three words, give some examples of usage of these words in English.
            """;

    private static final String ROLE_GUIDE_COMPLET = """
            Your are a travel guide. If the user type the name of a country or of a town,
            you tell them what are the main places to visit in the country or the town
            are you tell them the average price of a meal.
            """;

    private static final String ROLE_POETE_MAROCAIN = """
            You are a Moroccan poet who transforms everything into poetry.
            Your responses must ALWAYS be in the form of poetry (verses, rhymes, or free verse).
            - Use vivid imagery inspired by Morocco: the Atlas mountains, the Sahara desert, Casablanca's ocean, mint tea, argan trees, souks, tagines, medinas, etc.
            - Incorporate Arabic or Darija words naturally when appropriate (like "habibi", "shukran", "inchallah", "salam", "yallah", "choukran").
            - Your tone is warm, philosophical, and nostalgic.
            - Even if the user asks a simple question or makes a statement, transform it into beautiful poetry.
            - Keep responses concise (4-8 lines of poetry).
            - Every answer must be poetic, no exceptions.
            Example: If asked "what is the weather?", respond with a poem about the sky, clouds, and Moroccan landscapes.
            """;

    // =================================================================================
    // INJECTIONS CDI
    // =================================================================================

    /**
     * Contexte JSF. Utilisé pour qu'un message d'erreur s'affiche dans le formulaire.
     */
    @Inject
    private FacesContext facesContext;

    @Inject
    private JsonUtilPourGemini jsonUtil;

    // =================================================================================
    // PROPRIÉTÉS
    // =================================================================================

    /**
     * Code du rôle système sélectionné (ASSISTANT, TRADUCTEUR, GUIDE, POETE).
     */
    private String roleSystemeCode;

    /**
     * Quand le rôle est choisi par l'utilisateur dans la liste déroulante,
     * il n'est plus possible de le modifier (voir code de la page JSF), sauf si on veut un nouveau chat.
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste de tous les rôles de l'API prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;

    /**
     * Dernière réponse de l'API OpenAI.
     */
    private String reponse;

    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Texte JSON de la requête (pour debug).
     */
    private String texteRequeteJson;

    /**
     * Texte JSON de la réponse (pour debug).
     */
    private String texteReponseJson;

    /**
     * Mode debug activé/désactivé.
     */
    private boolean debug = true;

    // =================================================================================
    // CONSTRUCTEUR
    // =================================================================================

    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI), s'il y a un autre constructeur.
     */
    public Bb() {
        // Initialisation du rôle par défaut
        this.roleSystemeCode = "ASSISTANT";
    }

    // =================================================================================
    // ACTIONS PRINCIPALES
    // =================================================================================

    /**
     * Envoie la question au serveur.
     * Ajoute automatiquement le contexte temporel à chaque question.
     *
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        // Validation de la question
        if (question == null || question.isBlank()) {
            ajouterMessageErreur("Texte question vide", "Il manque le texte de la question");
            return null;
        }

        // Si la conversation n'a pas encore commencé, ajouter le rôle système au début
        if (this.conversation.isEmpty()) {
            String roleComplet = getRoleSystemeComplet();
            jsonUtil.setSystemRole(roleComplet);
            this.roleSystemeChangeable = false;
        }

        // Enrichissement contextuel : ajout du moment de la journée
        String questionAvecContexte = question + "\n[Information contextuelle: " + getMomentDeLaJournee() + "]";

        // 🔹 Envoi de la requête au LLM Gemini
        try {
            LlmInteraction interaction = jsonUtil.envoyerRequete(questionAvecContexte);
            this.reponse = interaction.reponseExtraite();
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.reponseJson();

        } catch (Exception e) {
            ajouterMessageErreur(
                    "Problème de connexion avec l'API du LLM",
                    "Problème de connexion avec l'API du LLM : " + e.getMessage()
            );
            this.reponse = "ERREUR : Voir le message ci-dessus.";
            this.texteRequeteJson = jsonUtil.getTexteRequeteJson();
            this.texteReponseJson = "Erreur : " + e.getMessage();
            return null;
        }

        // 🔹 Met à jour la conversation affichée à l'écran
        afficherConversation();
        return null;
    }

    /**
     * Pour un nouveau chat.
     * Termine la portée view en retournant "index" (la page index.xhtml sera affichée après le traitement
     * effectué pour construire la réponse) et pas null. null aurait indiqué de rester dans la même page (index.xhtml)
     * sans changer de vue.
     * Le fait de changer de vue va faire supprimer l'instance en cours du backing bean par CDI et donc on reprend
     * tout comme au début puisqu'une nouvelle instance du backing va être utilisée par la page index.xhtml.
     *
     * @return "index"
     */
    public String nouveauChat() {
        // Réinitialisation explicite de toutes les propriétés
        this.conversation = new StringBuilder();
        this.reponse = null;
        this.question = null;
        this.texteRequeteJson = null;
        this.texteReponseJson = null;
        this.roleSystemeChangeable = true;
        this.roleSystemeCode = "ASSISTANT";
        return "index";
    }

    /**
     * Bascule le mode debug (activé/désactivé).
     */
    public void toggleDebug() {
        this.debug = !this.debug;
    }

    // =================================================================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // =================================================================================

    /**
     * Retourne le texte complet du rôle système basé sur le code sélectionné.
     *
     * @return Le texte complet du rôle système.
     */
    private String getRoleSystemeComplet() {
        return switch (roleSystemeCode) {
            case "ASSISTANT" -> ROLE_ASSISTANT_COMPLET;
            case "TRADUCTEUR" -> ROLE_TRADUCTEUR_COMPLET;
            case "GUIDE" -> ROLE_GUIDE_COMPLET;
            case "POETE" -> ROLE_POETE_MAROCAIN;
            default -> ROLE_ASSISTANT_COMPLET;
        };
    }

    /**
     * Retourne le nom affiché du rôle système basé sur le code.
     *
     * @return Le nom du rôle pour l'affichage.
     */
    private String getRoleSystemeNom() {
        return switch (roleSystemeCode) {
            case "ASSISTANT" -> "Assistant";
            case "TRADUCTEUR" -> "Traducteur Anglais-Français";
            case "GUIDE" -> "Guide touristique";
            case "POETE" -> "Poète Marocain";
            default -> "Assistant";
        };
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     * N'affiche QUE les échanges User/Assistant (le rôle système n'apparaît pas ici).
     */
    private void afficherConversation() {
        // Ajouter uniquement l'échange question/réponse
        this.conversation.append("== User:\n")
                .append(question)
                .append("\n\n== Assistant:\n")
                .append(reponse)
                .append("\n\n")
                .append("─────────────────────────────────────\n\n");
    }

    /**
     * Détermine le moment de la journée pour enrichir le contexte de la question.
     *
     * @return Une chaîne décrivant le moment de la journée avec l'heure locale.
     */
    private String getMomentDeLaJournee() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        String heureFormatee = now.format(DateTimeFormatter.ofPattern("HH:mm"));

        if (hour >= 5 && hour < 12) {
            return "C'est le Matin (Heure locale : " + heureFormatee + ").";
        } else if (hour >= 12 && hour < 18) {
            return "C'est l'Après-midi (Heure locale : " + heureFormatee + ").";
        } else {
            return "C'est le Soir ou la Nuit (Heure locale : " + heureFormatee + ").";
        }
    }

    /**
     * Ajoute un message d'erreur au contexte JSF pour l'affichage dans le formulaire.
     *
     * @param resume Résumé de l'erreur.
     * @param detail Détails de l'erreur.
     */
    private void ajouterMessageErreur(String resume, String detail) {
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, resume, detail);
        facesContext.addMessage(null, message);
    }

    // =================================================================================
    // GETTERS ET SETTERS
    // =================================================================================

    public String getRoleSystemeCode() {
        return roleSystemeCode;
    }

    public void setRoleSystemeCode(String roleSystemeCode) {
        this.roleSystemeCode = roleSystemeCode;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    /**
     * setter indispensable pour le textarea.
     *
     * @param reponse la réponse à la question.
     */
    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    public String getTexteRequeteJson() {
        return texteRequeteJson;
    }

    public void setTexteRequeteJson(String texteRequeteJson) {
        this.texteRequeteJson = texteRequeteJson;
    }

    public String getTexteReponseJson() {
        return texteReponseJson;
    }

    public void setTexteReponseJson(String texteReponseJson) {
        this.texteReponseJson = texteReponseJson;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Retourne la liste des rôles système disponibles pour le menu déroulant.
     * Vous pouvez évidemment écrire ces rôles dans la langue que vous voulez.
     *
     * @return Liste des SelectItem pour la liste déroulante.
     */
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            // Génère les rôles de l'API prédéfinis
            this.listeRolesSysteme = new ArrayList<>();
            // 1er argument : le CODE du rôle, 2ème argument : le LIBELLÉ du rôle
            this.listeRolesSysteme.add(new SelectItem("ASSISTANT", "Assistant"));
            this.listeRolesSysteme.add(new SelectItem("TRADUCTEUR", "Traducteur Anglais-Français"));
            this.listeRolesSysteme.add(new SelectItem("GUIDE", "Guide touristique"));
            this.listeRolesSysteme.add(new SelectItem("POETE", "Poète Marocain"));
        }

        return this.listeRolesSysteme;
    }
}