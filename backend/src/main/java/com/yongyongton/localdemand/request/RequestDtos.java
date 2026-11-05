package com.yongyongton.localdemand.request;

import com.yongyongton.localdemand.offer.OfferDtos.ResidentOfferView;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** 주민 요청 API 요청·응답 (명세 5-3). */
public final class RequestDtos {

    private RequestDtos() {
    }

    /**
     * POST /requests 요청. 업종·시간대(1개 이상)·빈도는 필수, 문장·조건은 선택.
     * 목록에 없는 코드(예: "conditions": ["FOO"])가 오면 400.
     */
    public record CreateRequest(
            @NotNull Long dongId,
            @NotNull Long categoryId,
            @Size(max = 200) String text,
            List<RequestCondition> conditions,
            @NotEmpty List<TimeSlot> timeSlots,
            @NotNull Frequency frequency) {
    }

    /** POST /requests 201 응답. */
    public record CreateResponse(
            RequestSummary request,
            long count,
            int goal,
            boolean reachedGoal,
            long notifiedOwnerCount) {
    }

    public record RequestSummary(Long id, Long dongId, Long categoryId, OffsetDateTime createdAt) {
    }

    /** GET /me/requests 한 줄. */
    public record MyRequest(
            Long id,
            IdName dong,
            IdName category,
            long count,
            List<TimeSlot> timeSlots,
            Frequency frequency,
            OffsetDateTime createdAt,
            List<ResidentOfferView> offers) {
    }

    public record IdName(Long id, String name) {
    }
}
