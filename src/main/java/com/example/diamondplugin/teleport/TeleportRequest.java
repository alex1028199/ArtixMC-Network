package com.example.diamondplugin.teleport;

import java.util.UUID;

public record TeleportRequest(
        UUID requesterUuid,
        String requesterName, // Store name for messages, UUID is the key
        UUID targetUuid,
        String targetName,
        long requestTimestamp,
        boolean toRequester // true if target comes to requester, false if requester goes to target
) {
    public boolean hasExpired(long timeoutSeconds) {
        return (System.currentTimeMillis() - requestTimestamp) > (timeoutSeconds * 1000L);
    }
}
