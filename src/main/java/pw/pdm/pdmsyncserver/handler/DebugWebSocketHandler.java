package pw.pdm.pdmsyncserver.handler;

import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class DebugWebSocketHandler implements WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(DebugWebSocketHandler.class);

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        logger.info("Attempting to establish WebSocket connection: {}", session.getId());

        return session.send(Flux.interval(Duration.ofSeconds(30))
                        .map(i -> session.textMessage("Server heartbeat " + i))
                        .doOnSubscribe(s -> logger.info("Starting heartbeat for session: {}", session.getId()))
                        .doOnError(e -> logger.error("Error in heartbeat for session: {}", session.getId(), e))
                )
                .and(session.receive()
                        .doOnNext(message -> {
                            String payload = message.getPayloadAsText();
                            logger.info("Received message on session {}: {}", session.getId(), payload);
                            session.send(Mono.just(session.textMessage("Echo: " + payload)))
                                    .subscribe(
                                            null,
                                            error -> logger.error("Error sending echo for session: {}", session.getId(), error),
                                            () -> logger.info("Echo sent successfully for session: {}", session.getId())
                                    );
                        })
                        .doOnComplete(() -> logger.info("Receive completed for session: {}", session.getId()))
                        .doOnError(error -> logger.error("Error in receive flux for session: {}", session.getId(), error))
                )
                .doOnSubscribe(s -> logger.info("WebSocket session fully established: {}", session.getId()))
                .doOnError(error -> logger.error("Error in WebSocket session: {}", session.getId(), error))
                .doFinally(signalType -> logger.info("WebSocket session terminated: {}. Signal: {}", session.getId(), signalType));
    }
}