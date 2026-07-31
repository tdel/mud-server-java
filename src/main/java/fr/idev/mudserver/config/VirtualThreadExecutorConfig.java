package fr.idev.mudserver.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un virtual thread par ligne de commande traitée — jamais de logique bloquante
 * (JDBC compris) sur les threads NIO de Netty. Voir telnet.GameCommandHandler.
 */
@Configuration
public class VirtualThreadExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
