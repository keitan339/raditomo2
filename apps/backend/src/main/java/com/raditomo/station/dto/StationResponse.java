package com.raditomo.station.dto;

import com.raditomo.station.entity.Station;

public record StationResponse(
        String id,
        String areaId,
        String name,
        String asciiName,
        String logoUrl,
        int sortOrder
) {
    public static StationResponse from(Station s) {
        return new StationResponse(
                s.getId(), s.getAreaId(), s.getName(),
                s.getAsciiName(), s.getLogoUrl(), s.getSortOrder());
    }
}
