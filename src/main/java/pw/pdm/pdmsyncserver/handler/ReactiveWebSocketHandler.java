package pw.pdm.pdmsyncserver.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class ReactiveWebSocketHandler implements WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveWebSocketHandler.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return Mono.zip(
                        handleIncomingMessages(session),
                        sendHeartbeat(session)
                ).doOnSubscribe(tuple -> logSessionEstablished(session))
                .doOnError(error -> logSessionError(session, error))
                .doFinally(signalType -> logSessionTerminated(session, String.valueOf(signalType)))
                .then();
    }

    private Mono<Void> handleIncomingMessages(WebSocketSession session) {
        return session.receive()
                .flatMap(message -> processMessage(session, message))
                .then();
    }

    private Mono<Void> processMessage(WebSocketSession session, WebSocketMessage message) {
        String payload = message.getPayloadAsText();
        logger.info("Received message on session {}: {}", session.getId(), payload);
        return session.send(Mono.just(session.textMessage("Echo: " + payload)))
                .doOnSuccess(v -> logger.info("Echo sent successfully for session: {}", session.getId()))
                .doOnError(error -> logger.error("Error sending echo for session: {}", session.getId(), error));
    }

    private Mono<Void> sendHeartbeat(WebSocketSession session) {
        return session.send(Flux.interval(HEARTBEAT_INTERVAL)
                        .map(i -> session.textMessage("Server heartbeat " + i)))
                .doOnSubscribe(s -> logger.info("Starting heartbeat for session: {}", session.getId()))
                .doOnError(e -> logger.error("Error in heartbeat for session: {}", session.getId(), e));
    }

    private void logSessionEstablished(WebSocketSession session) {
        logger.info("WebSocket session fully established: {}", session.getId());
    }

    private void logSessionError(WebSocketSession session, Throwable error) {
        logger.error("Error in WebSocket session: {}", session.getId(), error);
    }

    private void logSessionTerminated(WebSocketSession session, String signalType) {
        logger.info("WebSocket session terminated: {}. Signal: {}", session.getId(), signalType);
    }
}