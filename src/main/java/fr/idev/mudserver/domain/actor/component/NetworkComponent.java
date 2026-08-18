package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.network.Connection;

public class NetworkComponent {

    public Connection connection;

    public NetworkComponent(Connection connection) {
        this.connection = connection;
    }
}
