package com.africevent.backend.exception;

import lombok.Getter;

@Getter
public class SmartPayException extends RuntimeException {

    /** Statut HTTP renvoye par SmartPay, ou 0 si aucune reponse n'a ete obtenue. */
    private final int httpStatus;
    private final String code;
    private final String requestId;

    public SmartPayException(String message, int httpStatus, String code, String requestId, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
        this.requestId = requestId;
    }

    /** Vrai pour une indisponibilite ou un incident temporaire : l'operation peut etre rejouee. */
    public boolean isTemporary() {
        return httpStatus == 0 || httpStatus == 429 || httpStatus >= 500;
    }
}