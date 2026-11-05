package com.yongyongton.localdemand.ai;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSummaryRepository extends JpaRepository<AiSummary, Long> {

    /** 같은 동·업종·화면·숫자로 저장해 둔 문장 (uk_ai_summary_input). */
    Optional<AiSummary> findByDongIdAndCategoryIdAndKindAndInputHash(
            Long dongId, Long categoryId, SummaryKind kind, String inputHash);
}
