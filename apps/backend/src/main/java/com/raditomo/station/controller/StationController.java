package com.raditomo.station.controller;

import com.raditomo.station.dto.StationResponse;
import com.raditomo.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationRepository stationRepository;

    @GetMapping
    public List<StationResponse> list(@RequestParam String areaId) {
        return stationRepository.findByAreaIdOrderBySortOrderAsc(areaId).stream()
                .map(StationResponse::from)
                .toList();
    }
}
