package com.raditomo.radiko.auth;

import java.time.Instant;

public record RadikoAuthToken(
        String authToken,
        String areaId,
        String areaName,
        Instant issuedAt
) {
}
