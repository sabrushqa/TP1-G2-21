package ma.emsi.tp1lakehal;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Named
@ViewScoped
public class Bb implements Serializable {

    // --- RÔLES SYSTÈME COMPLETS ---
    private static final String ROLE_ASSISTANT_COMPLET = """
            You are a helpful assistant. You help the user to find the information they need.
            If the user type a question, you answer it.
            """;

    private static final String ROLE_TRADUCTEUR_COMPLET = """
            You are a translator. Your ONLY job is to translate text.
            - If the user types in French, translate ONLY to English. Do not explain, just translate.
            - If the user types in English, translate ONLY to French. Do not explain, just translate.
            - If the input is a single word or short phrase (1-3 words), provide the translation AND 2-3 example sentences showing how to use it.
            - Do NOT answer questions. Do NOT provide explanations. ONLY translate.
            - Example: if user writes "c'est quoi flutter", translate to "what is flutter" (do not explain what Flutter is).
            """;

    private static final String ROLE_GUIDE_COMPLET = """
            Your are a travel guide. If the user type the name of a country or of a town,
            you tell them what are the main places to visit in the country or the town
            and you tell them the average price of a meal.
            """;

    private static final String ROLE_POETE_MAROCAIN = """
            You are a Moroccan poet with a deep love for Moroccan culture, traditions, and landscapes.
            Your responses must ALWAYS be in the form of poetry (verses, rhymes, or free verse).
            - Use vivid imagery inspired by Morocco: the Atlas mountains, the Sahara desert, Casablanca's ocean, mint tea, argan trees, souks, tagines, etc.
            - Incorporate Arabic or Darija words naturally when appropriate (like "habibi", "shukran", "inchallah", "salam").
            - Your tone is warm, philosophical, and nostalgic.
            - Even if the user asks a simple question, answer it poetically.
            - Keep responses concise (4-8 lines of poetry).
            Example: If asked "how are you?", respond with a short poem about the morning sun over the medina.
            """;

    // --- INJECTIONS ---
    @Inject
    private JsonUtilPourGemini jsonUtil;

    @Inject
    private FacesContext facesContext;

    // --- PROPRIÉTÉS ---
    private String roleSystemeCode;
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;
    private String question;
    private String reponse;
    private StringBuilder conversation = new StringBuilder();
    private String texteRequeteJson;
    private String texteReponseJson;
    private boolean debug = false;

    // --- CONSTRUCTEUR ---
    public Bb() {
        // Initialisation du rôle par défaut
        this.roleSystemeCode = "POETE";
    }

    // =================================================================================
    // ACTIONS PRINCIPALES
    // =================================================================================

    /**
     * Action pour envoyer la question de l'utilisateur au LLM.
     */
    public String envoyer() {
        // Validation de la question
        if (question == null || question.isBlank()) {
            ajouterMessageErreur("Texte question vide", "Il manque le texte de la question");
            return null;
        }

        // Début de conversation : envoyer le rôle système complet
        if (conversation.isEmpty()) {
            String roleComplet = getRoleSystemeComplet();
            jsonUtil.setSystemRole(roleComplet);
            roleSystemeChangeable = false;
        }

        // Appel à l'API LLM
        try {
            String questionAvecContexte = question + "\n[Information contextuelle: " + getMomentDeLaJournee() + "]";

            LlmInteraction interaction = jsonUtil.envoyerRequete(questionAvecContexte);

            this.reponse = interaction.reponseExtraite();
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.reponseJson();

        } catch (Exception e) {
            ajouterMessageErreur("Problème de connexion avec l'API du LLM",
                    "Problème de connexion avec l'API du LLM : " + e.getMessage());
            this.reponse = "ERREUR : Voir le message ci-dessus.";
            this.texteRequeteJson = jsonUtil.getTexteRequeteJson();
            this.texteReponseJson = "Erreur : " + e.getMessage();
            return null;
        }

        afficherConversation();
        return null;
    }

    /**
     * Réinitialise complètement le chat.
     */
    public String nouveauChat() {
        this.conversation = new StringBuilder();
        this.reponse = null;
        this.question = null;
        this.texteRequeteJson = null;
        this.texteReponseJson = null;
        this.roleSystemeChangeable = true;
        this.roleSystemeCode = "POETE";
        return "index";
    }

    /**
     * Bascule le mode debug.
     */
    public void toggleDebug() {
        this.debug = !this.debug;
    }

    // =================================================================================
    // MÉTHODES UTILITAIRES
    // =================================================================================

    /**
     * Retourne le texte complet du rôle système basé sur le code sélectionné.
     */
    private String getRoleSystemeComplet() {
        return switch (roleSystemeCode) {
            case "ASSISTANT" -> ROLE_ASSISTANT_COMPLET;
            case "TRADUCTEUR" -> ROLE_TRADUCTEUR_COMPLET;
            case "GUIDE" -> ROLE_GUIDE_COMPLET;
            case "POETE" -> ROLE_POETE_MAROCAIN;
            default -> ROLE_TRADUCTEUR_COMPLET;
        };
    }

    /**
     * Retourne le nom affiché du rôle système basé sur le code.
     */
    private String getRoleSystemeNom() {
        return switch (roleSystemeCode) {
            case "ASSISTANT" -> "Assistant";
            case "TRADUCTEUR" -> "Traducteur Anglais-Français";
            case "GUIDE" -> "Guide touristique";
            case "POETE" -> "Poète Marocain";
            default -> "Traducteur Anglais-Français";
        };
    }

    /**
     * Met à jour la zone de conversation avec la dernière interaction.
     */
    private void afficherConversation() {
        if (this.conversation.isEmpty()) {
            this.conversation.append("* Rôle Système (Initial): ")
                    .append(getRoleSystemeNom())
                    .append("\n")
                    .append(getRoleSystemeComplet())
                    .append("\n\n");
        }
        this.conversation.append("* User:\n")
                .append(question)
                .append("\n\n* Serveur:\n")
                .append(reponse)
                .append("\n\n");
    }

    /**
     * Détermine le moment de la journée pour enrichir le contexte.
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
     * Ajoute un message d'erreur au contexte JSF.
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
     */
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();
            this.listeRolesSysteme.add(new SelectItem("ASSISTANT", "Assistant"));
            this.listeRolesSysteme.add(new SelectItem("TRADUCTEUR", "Traducteur Anglais-Français"));
            this.listeRolesSysteme.add(new SelectItem("GUIDE", "Guide touristique"));
            this.listeRolesSysteme.add(new SelectItem("POETE", "Poète Marocain 🇲🇦"));
        }
        return this.listeRolesSysteme
    }
}