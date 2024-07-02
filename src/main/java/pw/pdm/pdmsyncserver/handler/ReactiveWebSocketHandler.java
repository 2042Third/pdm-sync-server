package pw.pdm.pdmsyncserver.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import pw.pdm.pdmsyncserver.service.HealthCheckService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReactiveWebSocketHandler implements WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveWebSocketHandler.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Instant> lastActivityTime = new ConcurrentHashMap<>();

    private final HealthCheckService healthCheckService;

    public ReactiveWebSocketHandler(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return Mono.zip(
                        handleIncomingMessages(session),
                        performHeartbeat(session)
                ).doOnSubscribe(tuple -> logSessionEstablished(session))
                .doOnError(error -> logSessionError(session, error))
                .doFinally(signalType -> {
                    logSessionTerminated(session, String.valueOf(signalType));
                    lastActivityTime.remove(session.getId());
                })
                .then();
    }

    private Mono<Void> handleIncomingMessages(WebSocketSession session) {
        return session.receive()
                .flatMap(message -> processMessage(session, message))
                .then();
    }

    private Mono<Void> processMessage(WebSocketSession session, WebSocketMessage message) {
        updateLastActivityTime(session);
        String payload = message.getPayloadAsText();
        logger.info("Received message on session {}: {}", session.getId(), payload);
        return session.send(Mono.just(session.textMessage("Echo: " + payload)))
                .doOnSuccess(v -> logger.info("Echo sent successfully for session: {}", session.getId()))
                .doOnError(error -> logger.error("Error sending echo for session: {}", session.getId(), error));
    }

    private Mono<Void> performHeartbeat(WebSocketSession session) {
        return Flux.interval(HEARTBEAT_INTERVAL)
                .flatMap(i -> sendHeartbeat(session))
                .then();
    }

    private Mono<Void> sendHeartbeat(WebSocketSession session) {
        return Flux.interval(HEARTBEAT_INTERVAL)
                .flatMap(tick -> healthCheckService.performHealthCheck())
                .flatMap(healthStatus -> {
                    String heartbeatMessage = String.format("Heartbeat: %s", healthStatus);
                    logger.info("Sending heartbeat to session {}: {}", session.getId(), heartbeatMessage);
                    return session.send(Mono.just(session.textMessage(heartbeatMessage)));
                })
                .then();
    }

    private Mono<Void> sendHeartbeatMessage(WebSocketSession session, String healthStatus) {
        String heartbeatMessage = String.format("Heartbeat: %s, Health: %s", Instant.now(), healthStatus);
        return session.send(Mono.just(session.textMessage(heartbeatMessage)));
    }

    private boolean isSessionIdle(WebSocketSession session) {
        Instant lastActivity = lastActivityTime.get(session.getId());
        return lastActivity != null && Duration.between(lastActivity, Instant.now()).compareTo(MAX_IDLE_TIME) > 0;
    }

    private Mono<Void> closeIdleSession(WebSocketSession session) {
        logger.info("Closing idle session: {}", session.getId());
        return session.close()
                .doFinally(signalType -> lastActivityTime.remove(session.getId()));
    }

    private void updateLastActivityTime(WebSocketSession session) {
        lastActivityTime.put(session.getId(), Instant.now());
    }

    private void logSessionEstablished(WebSocketSession session) {
        logger.info("WebSocket session fully established: {}", session.getId());
        updateLastActivityTime(session);
    }

    private void logSessionError(WebSocketSession session, Throwable error) {
        logger.error("Error in WebSocket session: {}", session.getId(), error);
    }

    private void logSessionTerminated(WebSocketSession session, String signalType) {
        logger.info("WebSocket session terminated: {}. Signal: {}", session.getId(), signalType);
    }
}