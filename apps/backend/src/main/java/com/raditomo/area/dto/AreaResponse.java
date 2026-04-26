package com.raditomo.area.dto;

import com.raditomo.area.entity.Area;

public record AreaResponse(String id, String name, int sortOrder) {
    public static AreaResponse from(Area a) {
        return new AreaResponse(a.getId(), a.getName(), a.getSortOrder());
    }
}
