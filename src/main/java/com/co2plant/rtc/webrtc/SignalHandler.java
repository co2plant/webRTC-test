package com.co2plant.rtc.webrtc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignalHandler extends TextWebSocketHandler {

    private final Logger log = LoggerFactory.getLogger(SignalHandler.class);
    private final KurentoClient kurentoClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

    @Autowired
    public SignalHandler(KurentoClient kurentoClient) {
        this.kurentoClient = kurentoClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("New WebSocket connection: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {}", session.getId());
        stop(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode jsonMessage = mapper.readTree(message.getPayload());
            String messageId = jsonMessage.get("id").asText();

            switch (messageId) {
                case "start":
                    start(session, jsonMessage);
                    break;
                case "stop":
                    stop(session);
                    break;
                case "onIceCandidate":
                    onIceCandidate(session, jsonMessage);
                    break;
                default:
                    sendError(session, "Invalid message with id " + messageId);
                    break;
            }
        } catch (Throwable t) {
            log.error("Exception handling message", t);
            sendError(session, t.getMessage());
        }
    }

    private void start(WebSocketSession session, JsonNode jsonMessage) {
        try {
            UserSession user = new UserSession(session);
            users.put(session.getId(), user);

            // 1. Media Pipeline
            MediaPipeline pipeline = kurentoClient.createMediaPipeline();
            
            // 2. Endpoint (WebRtcEndpoint)
            WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
            user.setWebRtcEndpoint(webRtcEndpoint);

            // 3. Loopback (Connect endpoint to itself)
            webRtcEndpoint.connect(webRtcEndpoint);

            // 4. SDP Negotiation (Process Offer)
            String sdpOffer = jsonMessage.get("sdpOffer").asText();
            String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

            // 5. Send Answer back to client
            ObjectNode response = mapper.createObjectNode();
            response.put("id", "startResponse");
            response.put("sdpAnswer", sdpAnswer);
            
            synchronized (session) {
                session.sendMessage(new TextMessage(response.toString()));
            }

            // 6. Gather Candidates
            webRtcEndpoint.gatherCandidates();

        } catch (Throwable t) {
            log.error("Start error", t);
            sendError(session, t.getMessage());
        }
    }

    private void stop(WebSocketSession session) {
        UserSession user = users.remove(session.getId());
        if (user != null) {
            user.release();
        }
    }

    private void onIceCandidate(WebSocketSession session, JsonNode jsonMessage) {
        try {
            UserSession user = users.get(session.getId());
            if (user != null) {
                JsonNode candidateNode = jsonMessage.get("candidate");
                IceCandidate candidate = new IceCandidate(
                        candidateNode.get("candidate").asText(),
                        candidateNode.get("sdpMid").asText(),
                        candidateNode.get("sdpMLineIndex").asInt()
                );
                user.addCandidate(candidate);
            }
        } catch (Exception e) {
            log.error("ICE Candidate error", e);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("id", "error");
            response.put("message", message);
            synchronized (session) {
                session.sendMessage(new TextMessage(response.toString()));
            }
        } catch (IOException e) {
            log.error("Exception sending error", e);
        }
    }
}
