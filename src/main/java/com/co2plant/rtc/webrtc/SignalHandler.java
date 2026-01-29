package com.co2plant.rtc.webrtc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.ConcurrentHashMap;
import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class SignalHandler extends TextWebSocketHandler {

    private final Logger log = LoggerFactory.getLogger(SignalHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();
    
    private final RoomManager roomManager;
    private final UserRegistry registry = new UserRegistry(); // Simple registry to map SessionID -> UserSession

    @Autowired
    public SignalHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode jsonMessage = mapper.readTree(message.getPayload());
            String messageId = jsonMessage.get("id").asText();

            log.debug("Message received: {}", messageId);

            switch (messageId) {
                case "joinRoom":
                    joinRoom(jsonMessage, session);
                    break;
                case "receiveVideoFrom":
                    String senderName = jsonMessage.get("sender").asText();
                    String sdpOffer = jsonMessage.get("sdpOffer").asText();
                    receiveVideoFrom(session, senderName, sdpOffer);
                    break;
                case "leaveRoom":
                    leaveRoom(session);
                    break;
                case "onIceCandidate":
                    JsonNode candidate = jsonMessage.get("candidate");
                    onIceCandidate(session, candidate);
                    break;
                default:
                    // Loopback "start" message from previous test might still arrive if cached, ignore or handle error
                    break;
            }
        } catch (Throwable t) {
            log.error("Exception handling message", t);
        }
    }

    private void joinRoom(JsonNode params, WebSocketSession session) throws IOException {
        String roomName = params.get("room").asText();
        String name = params.get("name").asText();
        // Default to "user" if role is missing
        String role = params.has("role") ? params.get("role").asText() : "user";

        log.info("PARTICIPANT {}: trying to join room {} as {}", name, roomName, role);

        Room room = roomManager.getRoom(roomName);
        UserSession user = room.join(name, role, session);
        registry.register(user);
    }

    private void leaveRoom(WebSocketSession session) throws IOException {
        UserSession user = registry.getBySession(session);
        if (user != null) {
            Room room = roomManager.getRoom(user.getRoomName());
            room.leave(user);
            if (room.getParticipantNames().isEmpty()) {
                roomManager.removeRoom(room);
            }
            registry.removeBySession(session);
        }
    }

    private void receiveVideoFrom(WebSocketSession session, String senderName, String sdpOffer) throws IOException {
        UserSession user = registry.getBySession(session);
        
        if (user.getName().equals(senderName)) {
            // User is sending their own video (Publishing)
            user.receiveFromClient(sdpOffer);
        } else {
            // User wants to view someone else's video (Subscribing)
            UserSession sender = roomManager.getRoom(user.getRoomName()).getParticipant(senderName);
            if (sender != null) {
                user.receiveVideoFrom(sender, sdpOffer);
            }
        }
    }

    private void onIceCandidate(WebSocketSession session, JsonNode jsonCandidate) {
        UserSession user = registry.getBySession(session);
        if (user != null) {
            JsonNode candidateParam = jsonCandidate.get("candidate");
            JsonNode sdpMidParam = jsonCandidate.get("sdpMid");
            JsonNode sdpMLineIndexParam = jsonCandidate.get("sdpMLineIndex");
            
            // "name" param in the message tells us WHICH connection this candidate belongs to
            // If name == user.getName(), it's for the OUTGOING connection
            // If name != user.getName(), it's for the INCOMING connection from 'name'
            String endpointName = jsonCandidate.has("name") ? jsonCandidate.get("name").asText() : user.getName();

            // Handle potential nulls
            String sdpMid = sdpMidParam != null ? sdpMidParam.asText() : null;
            int sdpMLineIndex = sdpMLineIndexParam != null ? sdpMLineIndexParam.asInt() : 0;
            String candidateStr = candidateParam != null ? candidateParam.asText() : "";

            IceCandidate cand = new IceCandidate(candidateStr, sdpMid, sdpMLineIndex);
            user.addCandidate(cand, endpointName);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        leaveRoom(session);
    }
    
    // Simple inner class for Registry (could be separate file)
    static class UserRegistry {
        private final ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<>();

        public void register(UserSession user) {
            usersBySessionId.put(user.getSession().getId(), user);
        }

        public UserSession getBySession(WebSocketSession session) {
            return usersBySessionId.get(session.getId());
        }

        public UserSession removeBySession(WebSocketSession session) {
            return usersBySessionId.remove(session.getId());
        }
    }
}
