package com.africevent.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartPayWebhookEvent {

    private String id;
    private String type;
    private Long created;
    private Payload data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private SmartPaySession session;
    }
}