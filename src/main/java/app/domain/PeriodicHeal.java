package app.domain;

// Soin régulier (Greater Heal, Greater Group Heal) : `amount` PV toutes les
// `intervalMs` tant que l'effet porteur dure.
public record PeriodicHeal(int amount, long intervalMs) {
}
