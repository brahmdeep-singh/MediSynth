package com.personal.medisynth.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * outcome = ACTIVE            -> patient: user + accessToken present, may use the app immediately.
 * outcome = PENDING_APPROVAL  -> doctor etc.: NO token, registrationReference present (shown once).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterResponse(
        String outcome,
        String message,
        UserSummary user,
        String accessToken,
        String tokenType,
        String registrationReference) {

    public static RegisterResponse active(UserSummary user, String token) {
        return new RegisterResponse("ACTIVE", "Registration successful. You can use MediSynth now.", user, token,
                "Bearer", null);
    }

    public static RegisterResponse pending(String reference) {
        return new RegisterResponse("PENDING_APPROVAL",
                "Your registration request was submitted and is awaiting admin approval. "
                        + "Keep your registration reference to check the status.",
                null, null, null, reference);
    }
}
