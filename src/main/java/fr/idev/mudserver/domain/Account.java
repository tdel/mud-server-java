package fr.idev.mudserver.domain;

import java.util.Objects;
import java.util.UUID;

public class Account {

    private UUID id;
    private String login;
    private String password;
    private UUID currentCharacterId;

    public Account(UUID id, String login, String password, UUID currentCharacterId) {
        this.id = id;
        this.login = login;
        this.password = password;
        this.currentCharacterId = currentCharacterId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UUID getCurrentCharacterId() {
        return currentCharacterId;
    }

    public void setCurrentCharacterId(UUID currentCharacterId) {
        this.currentCharacterId = currentCharacterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(login, other.login)
                && Objects.equals(password, other.password)
                && Objects.equals(currentCharacterId, other.currentCharacterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, login, password, currentCharacterId);
    }

    @Override
    public String toString() {
        return "Account[id=" + id + ", login=" + login + ", password=" + password + ", currentCharacterId="
                + currentCharacterId + "]";
    }
}
