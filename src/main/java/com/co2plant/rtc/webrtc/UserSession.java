package com.co2plant.rtc.webrtc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UserSession implements Closeable {

    private final Logger log = LoggerFactory.getLogger(UserSession.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final String name;
    private final String role; // "user" or "manager"
    private final WebSocketSession session;
    private final MediaPipeline pipeline;
    private final String roomName;

    // "outgoingMedia" is the user's OWN video (sending to Kurento)
    private final WebRtcEndpoint outgoingMedia;
    
    // "incomingMedia" is the video FROM others (receiving from Kurento)
    // Key: remote user's name
    private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();
    
    // Buffer for candidates that arrive before the endpoint is created
    private final ConcurrentMap<String, List<IceCandidate>> candidateBuffer = new ConcurrentHashMap<>();

    public UserSession(String name, String role, String roomName, WebSocketSession session, MediaPipeline pipeline) {
        this.pipeline = pipeline;
        this.name = name;
        this.role = role;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();
        
        // Add ICE listener for the OUTGOING endpoint (user's own video)
        this.outgoingMedia.addIceCandidateFoundListener(event -> {
            ObjectNode response = mapper.createObjectNode();
            response.put("id", "iceCandidate");
            response.put("name", name); // "name" corresponds to the user who generated this candidate
            addCandidateData(response, event.getCandidate());
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(response.toString()));
                }
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        });
    }

    public WebRtcEndpoint getOutgoingWebRtcPeer() {
        return outgoingMedia;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public WebSocketSession getSession() {
        return session;
    }
    
    public String getRoomName() {
        return roomName;
    }

    /**
     * Called when the client sends their OWN video (Publishing).
     * We need to process the SDP Offer on the 'outgoingMedia' endpoint.
     */
    public void receiveFromClient(String sdpOffer) throws IOException {
        log.info("USER {}: Negotiating outgoing connection", this.name);
        
        String sdpAnswer = outgoingMedia.processOffer(sdpOffer);
        
        ObjectNode response = mapper.createObjectNode();
        response.put("id", "receiveVideoAnswer");
        response.put("name", this.name);
        response.put("sdpAnswer", sdpAnswer);
        
        sendMessage(response);
        outgoingMedia.gatherCandidates();
    }

    /**
     * Called when THIS user wants to receive video FROM 'sender'.
     */
    public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
        log.info("USER {}: connecting with {} in room {}", this.name, sender.getName(), this.roomName);

        log.trace("USER {}: SdpOffer for {} is {}", this.name, sender.getName(), sdpOffer);

        // 1. Create a NEW endpoint for receiving the sender's video
        final WebRtcEndpoint incoming = new WebRtcEndpoint.Builder(pipeline).build();
        this.incomingMedia.put(sender.getName(), incoming);

        log.trace("USER {}: Created incoming endpoint for {}", this.name, sender.getName());

        // 2. Connect the SENDER's outgoing endpoint -> THIS incoming endpoint
        sender.getOutgoingWebRtcPeer().connect(incoming);

        // 3. Add ICE listener for THIS incoming endpoint
        // When Kurento generates a candidate for THIS receiving link, send it to the client
        incoming.addIceCandidateFoundListener(event -> {
            ObjectNode response = mapper.createObjectNode();
            response.put("id", "iceCandidate");
            response.put("name", sender.getName()); // It's a candidate for the link with 'sender'
            addCandidateData(response, event.getCandidate());
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(response.toString()));
                }
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        });
        
        // Process buffered candidates
        List<IceCandidate> buffered = candidateBuffer.remove(sender.getName());
        if (buffered != null) {
            log.info("USER {}: Replaying {} buffered candidates for {}", this.name, buffered.size(), sender.getName());
            for (IceCandidate c : buffered) {
                incoming.addIceCandidate(c);
            }
        }

        // 4. Process the SDP Offer
        String sdpAnswer = incoming.processOffer(sdpOffer);
        
        // 5. Send Answer back to client
        ObjectNode response = mapper.createObjectNode();
        response.put("id", "receiveVideoAnswer");
        response.put("name", sender.getName());
        response.put("sdpAnswer", sdpAnswer);

        log.trace("USER {}: SdpAnswer for {} is {}", this.name, sender.getName(), sdpAnswer);
        sendMessage(response);
        
        incoming.gatherCandidates();
    }
    
    /**
     * Called when the client sends an ICE candidate.
     * We must determine if it belongs to the OUTGOING connection or one of the INCOMING connections.
     */
    public void addCandidate(IceCandidate candidate, String name) {
        if (this.name.compareTo(name) == 0) {
            // It's for my own outgoing connection
            outgoingMedia.addIceCandidate(candidate);
        } else {
            // It's for a receiving connection from 'name'
            WebRtcEndpoint incoming = incomingMedia.get(name);
            if (incoming != null) {
                incoming.addIceCandidate(candidate);
            } else {
                log.info("USER {}: Buffering candidate for {}", this.name, name);
                candidateBuffer.computeIfAbsent(name, k -> new ArrayList<>()).add(candidate);
            }
        }
    }

    private void addCandidateData(ObjectNode response, IceCandidate candidate) {
        ObjectNode candidateJson = mapper.createObjectNode();
        candidateJson.put("candidate", candidate.getCandidate());
        candidateJson.put("sdpMid", candidate.getSdpMid());
        candidateJson.put("sdpMLineIndex", candidate.getSdpMLineIndex());
        response.set("candidate", candidateJson);
    }

    public void sendMessage(ObjectNode message) throws IOException {
        log.debug("USER {}: Sending message {}", name, message);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message.toString()));
            }
        }
    }
    
    public void cancelVideoFrom(String senderName) {
        log.debug("USER {}: canceling video subscription from {}", this.name, senderName);
        WebRtcEndpoint incoming = incomingMedia.remove(senderName);
        if (incoming != null) {
            incoming.release();
        }
    }

    @Override
    public void close() throws IOException {
        log.debug("PARTICIPANT {}: Releasing resources", this.name);
        for (final String remoteParticipantName : incomingMedia.keySet()) {
            log.trace("PARTICIPANT {}: Released incoming EP for {}", this.name, remoteParticipantName);
            final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);
            ep.release();
        }
        outgoingMedia.release();
        incomingMedia.clear();
    }
}
