package pw.pdm.pdmsyncserver.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class CustomWebSocketHandler implements WebSocketHandler {

    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final WebClient webClient;

    public CustomWebSocketHandler(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://10.0.0.189").build();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionKey = getSessionKey(session);
        return validateSession(sessionKey)
                .flatMap(isValid -> {
                    if (isValid) {
                        String userId = getUserIdFromSession(sessionKey);
                        return addSession(userId, session)
                                .then(handleMessages(session, userId))
                                .then(removeSession(userId, session));
                    } else {
                        return session.close();
                    }
                });
    }

    private Mono<Boolean> validateSession(String sessionKey) {
        return webClient.get()
                .uri("/api/user/validate")
                .header("Session-Key", sessionKey)
                .retrieve()
                .bodyToMono(Boolean.class);
    }

    private Mono<Void> addSession(String userId, WebSocketSession session) {
        return Mono.fromRunnable(() ->
                userSessions.compute(userId, (key, sessions) -> {
                    if (sessions == null) {
                        sessions = new CopyOnWriteArraySet<>();
                    }
                    if (sessions.size() < MAX_CONNECTIONS_PER_USER) {
                        sessions.add(session);
                    }
                    return sessions;
                })
        );
    }

    private Mono<Void> handleMessages(WebSocketSession session, String userId) {
        return session.receive()
                .flatMap(message -> broadcastMessage(userId, message.getPayloadAsText()))
                .then();
    }

    private Mono<Void> broadcastMessage(String userId, String message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            return Flux.fromIterable(sessions)
                    .flatMap(s -> s.send(Mono.just(s.textMessage(message))))
                    .then();
        }
        return Mono.empty();
    }

    private Mono<Void> removeSession(String userId, WebSocketSession session) {
        return Mono.fromRunnable(() ->
                userSessions.computeIfPresent(userId, (key, sessions) -> {
                    sessions.remove(session);
                    return sessions.isEmpty() ? null : sessions;
                })
        );
    }

    private String getSessionKey(WebSocketSession session) {
        return session.getHandshakeInfo().getHeaders().getFirst("Session-Key");
    }

    private String getUserIdFromSession(String sessionKey) {
        // Implement logic to extract user ID from session key
        return sessionKey;
    }
}