package com.co2plant.rtc.webrtc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Room implements Closeable {
    private final Logger log = LoggerFactory.getLogger(Room.class);

    private final String name;
    private final MediaPipeline pipeline;
    private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public Room(String name, MediaPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
        log.info("ROOM {} has been created", name);
    }

    public String getName() {
        return name;
    }

    public MediaPipeline getPipeline() {
        return pipeline;
    }

    public UserSession join(String userName, String role, WebSocketSession session) throws IOException {
        log.info("ROOM {}: adding participant {}, role {}", name, userName, role);
        final UserSession participant = new UserSession(userName, role, this.name, session, this.pipeline);
        joinRoom(participant);
        participants.put(participant.getName(), participant);
        sendParticipantNames(participant);
        return participant;
    }

    public void leave(UserSession user) throws IOException {
        log.debug("PARTICIPANT {}: Leaving room {}", user.getName(), this.name);
        this.removeParticipant(user.getName());
        user.close();
    }

    private Collection<UserSession> getParticipants() {
        return participants.values();
    }
    
    public Collection<String> getParticipantNames() {
        return participants.keySet();
    }

    private void joinRoom(UserSession newParticipant) throws IOException {
        final ObjectNode newParticipantMsg = mapper.createObjectNode();
        newParticipantMsg.put("id", "newParticipantArrived");
        newParticipantMsg.put("name", newParticipant.getName());
        newParticipantMsg.put("role", newParticipant.getRole());

        final List<String> participantsList = new ArrayList<>(participants.values().size());
        log.debug("ROOM {}: notifying other participants of new participant {}", name, newParticipant.getName());

        for (final UserSession participant : participants.values()) {
            try {
                participant.sendMessage(newParticipantMsg);
            } catch (final IOException e) {
                log.debug("ROOM {}: participant {} could not be notified", name, participant.getName(), e);
            }
            participantsList.add(participant.getName());
        }
    }

    private void removeParticipant(String name) throws IOException {
        participants.remove(name);

        log.debug("ROOM {}: notifying all users that {} is leaving the room", this.name, name);

        final List<String> unnotifiedParticipants = new ArrayList<>();
        final ObjectNode participantLeftJson = mapper.createObjectNode();
        participantLeftJson.put("id", "participantLeft");
        participantLeftJson.put("name", name);

        for (final UserSession participant : participants.values()) {
            try {
                participant.cancelVideoFrom(name);
                participant.sendMessage(participantLeftJson);
            } catch (final IOException e) {
                unnotifiedParticipants.add(participant.getName());
            }
        }

        if (!unnotifiedParticipants.isEmpty()) {
            log.debug("ROOM {}: The users {} could not be notified that {} left the room", this.name,
                    unnotifiedParticipants, name);
        }
    }

    public void sendParticipantNames(UserSession user) throws IOException {
        final ObjectNode existingParticipantsMsg = mapper.createObjectNode();
        existingParticipantsMsg.put("id", "existingParticipants");
        
        // Construct array of objects {name, role} instead of just strings
        List<ObjectNode> participantList = new ArrayList<>();
        for (UserSession p : getParticipants()) {
             if (!p.getName().equals(user.getName())) { // Exclude self from "existing" list
                 ObjectNode pNode = mapper.createObjectNode();
                 pNode.put("name", p.getName());
                 pNode.put("role", p.getRole());
                 participantList.add(pNode);
             }
        }
        
        existingParticipantsMsg.set("data", mapper.valueToTree(participantList));
        log.debug("PARTICIPANT {}: sending a list of {} participants", user.getName(),
                participantList.size());
        user.sendMessage(existingParticipantsMsg);
    }

    @Override
    public void close() {
        for (final UserSession user : participants.values()) {
            try {
                user.close();
            } catch (IOException e) {
                log.debug("ROOM {}: Could not close participant {}", this.name, user.getName(), e);
            }
        }
        participants.clear();
        pipeline.release(new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                log.trace("ROOM {}: Released Pipeline", Room.this.name);
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                log.warn("ROOM {}: Could not release Pipeline", Room.this.name);
            }
        });
        log.debug("Room {} closed", this.name);
    }

    public UserSession getParticipant(String name) {
        return participants.get(name);
    }
}
