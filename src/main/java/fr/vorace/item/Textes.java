package fr.vorace.item;

import fr.vorace.Messages;
import net.kyori.adventure.text.Component;

import java.text.Normalizer;
import java.util.Locale;

/** Textes de vorace-item : MiniMessage (avec les balises de Vorace), nombres à la française, clés sans accent. */
public final class Textes {

    public static final String PREFIXE = "<gradient:#f6b73c:#e8552d><bold>VoraceItem</bold></gradient> <dark_gray>»</dark_gray> ";

    private Textes() {}

    /** Texte d'objet ou de menu (MiniMessage, sans l'italique que le jeu ajoute). */
    public static Component objet(String miniMessage) {
        return Messages.objet(miniMessage);
    }

    /** Message précédé du préfixe de vorace-item. */
    public static Component message(String miniMessage) {
        return Messages.mini(PREFIXE + miniMessage);
    }

    public static String echapper(String s) {
        return Messages.echapper(s == null ? "" : s);
    }

    /** Nombre lisible : « 12 », « 12,5 », « 0,25 » (au plus deux décimales, virgule française). */
    public static String nombre(double v) {
        double arrondi = Math.round(v * 100.0) / 100.0;
        if (arrondi == Math.rint(arrondi)) return String.valueOf((long) arrondi);
        String s = String.format(Locale.ROOT, "%.2f", arrondi);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s.replace('.', ',');
    }

    /** Nombre à une décimale au plus (stats principales). */
    public static String nombre1(double v) {
        return nombre(Math.round(v * 10.0) / 10.0);
    }

    /** Clé de configuration : minuscules, sans accent, espaces remplacés par « _ ». */
    public static String cle(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('œ', 'o').replace(' ', '_').replace('\'', '_');
    }

    /** Première lettre en majuscule. */
    public static String majuscule(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
