package fr.idev.mudserver.telnet;

/**
 * Vue restreinte d'une session telnet visible par les classes de message : seulement écrire
 * du texte. Tout le reste (état, compte, monde...) reste invisible depuis un message de sortie.
 */
public interface TelnetOutput {
    void write(String text);
}
