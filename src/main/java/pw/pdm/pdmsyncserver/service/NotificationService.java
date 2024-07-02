package pw.pdm.pdmsyncserver.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final Sinks.Many<ServerSentEvent<String>> sink;

    private final Map<String, Instant> lastActivityMap = new ConcurrentHashMap<>();
    private static final Duration CLIENT_TIMEOUT = Duration.ofMinutes(5);

    public NotificationService() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
        this.sink.asFlux().subscribe(
                event -> { /* no-op */ },
                error -> { /* no-op */ },
                () -> { /* no-op */ }
        );
        logger.info("NotificationService initialized");
        startClientTimeoutChecker();
        startHeartbeat();
    }

    public Flux<ServerSentEvent<String>> getNotificationFlux() {

        return sink.asFlux()
                .doOnSubscribe(subscription -> logger.info("NotificationService client subscribed to SSE"))
                .doOnNext(event -> logger.debug("NotificationService event: {}", event))
                .doOnCancel(() -> logger.info("NotificationService Client unsubscribed from SSE"))
                .doOnComplete(() -> logger.info("NotificationService SSE stream completed"))
                .doOnError(error -> logger.error("NotificationService Error in SSE stream", error));
//                .mergeWith(Flux.interval(Duration.ofSeconds(30))
//                        .map(i -> ServerSentEvent.<String>builder()
//                                .id(String.valueOf(System.currentTimeMillis()))
//                                .event("heartbeat")
//                                .data("")
//                                .build()));
    }

    public void sendNotification(String message) {
        logger.info("Sending notification: {}", message);
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("notification")
                .data(String.format("{\"message\":\"%s\"}", message))
                .build();
        sink.tryEmitNext(event);
    }

    private void startClientTimeoutChecker() {
        Flux.interval(Duration.ofMinutes(1))
                .subscribe(tick -> {
                    Instant now = Instant.now();
                    lastActivityMap.entrySet().removeIf(entry ->
                            Duration.between(entry.getValue(), now).compareTo(CLIENT_TIMEOUT) > 0
                    );
                });
    }

    private void startHeartbeat() {
        Flux.interval(Duration.ofSeconds(30))
                .subscribe(tick -> {
                    ServerSentEvent<String> heartbeat = ServerSentEvent.<String>builder()
                            .id(String.valueOf(System.currentTimeMillis()))
                            .event("heartbeat")
                            .data("")
                            .build();
                    Sinks.EmitResult result = sink.tryEmitNext(heartbeat);
                    if (result.isFailure()) {
                        logger.error("Failed to emit heartbeat: {}", result);
                    } else {
                        logger.debug("Heartbeat sent");
                    }
                });
    }
}