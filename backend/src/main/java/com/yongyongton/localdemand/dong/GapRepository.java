package com.yongyongton.localdemand.dong;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GapRepository extends JpaRepository<Gap, Long> {

    List<Gap> findByDongIdAndStatus(Long dongId, String status);
}
