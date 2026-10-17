package com.medifind.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifind.model.ChatMessage;
import com.medifind.model.ChatMessage.SenderType;
import com.medifind.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, WebSocketSession> shopSessions = new ConcurrentHashMap<>();
    private final Map<Long, WebSocketSession> customerSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        Map<String, String> params = parseQuery(session.getUri().getQuery());
        String type = params.get("type");
        Long id = Long.valueOf(params.get("id"));
        if ("shop".equals(type)) {
            shopSessions.put(id, session);
        } else if ("customer".equals(type)) {
            customerSessions.put(id, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        Long shopId = Long.valueOf(payload.get("shopId").toString());
        Long customerId = Long.valueOf(payload.get("customerId").toString());
        String customerName = (String) payload.get("customerName");
        SenderType senderType = SenderType.valueOf((String) payload.get("senderType"));
        String text = (String) payload.get("message");

        ChatMessage saved = chatService.sendMessage(shopId, customerId, customerName, senderType, text);

        WebSocketSession shopSession = shopSessions.get(shopId);
        if (shopSession != null && shopSession.isOpen()) {
            shopSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(saved)));
        }
        WebSocketSession customerSession = customerSessions.get(customerId);
        if (customerSession != null && customerSession.isOpen()) {
            customerSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(saved)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String path = session.getUri().getPath();
        Map<String, String> params = parseQuery(session.getUri().getQuery());
        String type = params.get("type");
        Long id = Long.valueOf(params.get("id"));
        if ("shop".equals(type)) {
            shopSessions.remove(id);
        } else if ("customer".equals(type)) {
            customerSessions.remove(id);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new java.util.HashMap<>();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        }
        return result;
    }
}
