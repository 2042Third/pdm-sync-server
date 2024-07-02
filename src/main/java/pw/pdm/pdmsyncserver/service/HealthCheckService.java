package pw.pdm.pdmsyncserver.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class HealthCheckService {
    private final Instant startTime = Instant.now();

    public Mono<String> performHealthCheck() {
        return Mono.fromSupplier(() -> {
            long uptime = Duration.between(startTime, Instant.now()).toSeconds();
            return String.format("Uptime: %d seconds", uptime);
        });
    }
}