package app.network.server.tcpjson;

public interface TcpJsonOutput {
    void write(String type, Object payload);
}
