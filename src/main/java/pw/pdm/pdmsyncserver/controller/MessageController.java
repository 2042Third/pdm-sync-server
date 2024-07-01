package pw.pdm.pdmsyncserver.controller;


import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {

    @MessageMapping("/message")
    @SendTo("/topic/replies")
    public String processMessage(String message) throws Exception {
        return "Echo: " + message;
    }
}
