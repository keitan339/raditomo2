package com.raditomo.area.controller;

import com.raditomo.area.dto.AreaResponse;
import com.raditomo.area.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaRepository areaRepository;

    @GetMapping
    public List<AreaResponse> list() {
        return areaRepository.findAllByOrderBySortOrderAsc().stream()
                .map(AreaResponse::from)
                .toList();
    }
}
