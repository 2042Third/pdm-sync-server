package pw.pdm.pdmsyncserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import pw.pdm.pdmsyncserver.controller.model.NotificationRequest;
import pw.pdm.pdmsyncserver.handler.ReactiveWebSocketHandler;
import pw.pdm.pdmsyncserver.service.HealthCheckService;
import pw.pdm.pdmsyncserver.service.NotificationService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;


@Configuration
public class ServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);

    private final HealthCheckService healthCheckService;
    private final NotificationService notificationService;

    public ServerConfig(HealthCheckService healthCheckService, NotificationService notificationService) {
        this.healthCheckService = healthCheckService;
        this.notificationService = notificationService;
    }

    @Bean
    public RouterFunction<ServerResponse> sseRoutes() {
        return route(GET("/sse-stream/notification"), this::handleSseConnection)
                .andRoute(POST("/sse-stream/send-notification"), this::handleSendNotification);
    }

    public Mono<ServerResponse> handleSseConnection(ServerRequest request) {
        logger.info("SSE connection request received");
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromServerSentEvents(
                        Flux.concat(
                                        Flux.just(ServerSentEvent.<String>builder()
                                                .event("connected")
                                                .data("")
                                                .build()),
                                        notificationService.getNotificationFlux()
                                )
                                .doOnSubscribe(subscription -> logger.info("Client subscribed to SSE"))
                                .doOnNext(event -> logger.debug("Sending event: {}", event))
                                .doOnCancel(() -> logger.info("Client unsubscribed from SSE"))
                                .doOnComplete(() -> logger.info("SSE stream completed"))
                                .doOnError(error -> logger.error("Error in SSE stream", error))
                ));
    }

    public Mono<ServerResponse> handleSendNotification(ServerRequest request) {
        return request.bodyToMono(NotificationRequest.class)
                .flatMap(notificationRequest -> {
                    notificationService.sendNotification(notificationRequest.getMessage());
                    return ServerResponse.ok()
                            .body(BodyInserters.fromValue("Notification sent"));
                });
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws", new ReactiveWebSocketHandler(healthCheckService));
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}