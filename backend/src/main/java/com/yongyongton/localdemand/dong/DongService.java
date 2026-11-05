package com.yongyongton.localdemand.dong;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DongService {

    private final DongRepository dongRepository;

    public DongService(DongRepository dongRepository) {
        this.dongRepository = dongRepository;
    }

    public List<Dong> getDongs() {
        return dongRepository.findAll();
    }
}
