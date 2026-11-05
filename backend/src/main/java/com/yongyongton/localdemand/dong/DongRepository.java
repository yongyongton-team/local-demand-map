package com.yongyongton.localdemand.dong;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DongRepository extends JpaRepository<Dong, Long> {

    List<Dong> findAllByOrderByGuAscNameAsc();
}
