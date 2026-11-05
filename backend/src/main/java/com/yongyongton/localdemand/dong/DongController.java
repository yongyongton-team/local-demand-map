package com.yongyongton.localdemand.dong;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DongController {

    private final DongService dongService;

    public DongController(DongService dongService) {
        this.dongService = dongService;
    }

    @GetMapping("/dongs")
    public List<DongResponse> getDongs() {
        return dongService.getDongs().stream()
                .map(dong -> new DongResponse(
                        dong.getId(),
                        dong.getName(),
                        dong.getGu()
                ))
                .toList();
    }

    public record DongResponse(Long id, String name, String gu) {
    }
}
