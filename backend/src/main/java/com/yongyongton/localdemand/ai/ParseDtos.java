package com.yongyongton.localdemand.ai;

import com.yongyongton.localdemand.request.RequestCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** POST /requests/parse 요청·응답 (명세 5-3, 화면 2). */
public final class ParseDtos {

    private ParseDtos() {
    }

    /** 예: { "dongId": 101, "text": "밤 10시까지 하는 과일가게요." } */
    public record ParseRequest(@NotNull Long dongId, @NotBlank @Size(max = 200) String text) {
    }

    /**
     * 정리 결과. 저장하지 않아요.
     *
     * @param category         고른 업종. 못 정하면 null → 화면에 "업종을 골라 주세요"와 candidates 칩
     * @param conditions       문장에서 찾은 조건
     * @param alreadyRequested 이 동네에서 이 업종을 이미 요청했으면 true → 보내기 버튼을 막아요
     * @param candidates       업종을 못 정했을 때 고를 만한 업종(최대 3개). 정했으면 []
     * @param by               누가 정리했는지. AI = AI 모델, KEYWORD = AI 없이 낱말 맞추기(AI_PROVIDER=fake)
     */
    public record ParseResponse(IdName category, List<ConditionView> conditions, boolean alreadyRequested,
                                List<IdName> candidates, String by) {
    }

    public record IdName(Long id, String name) {
    }

    /** 예: { "code": "LATE_OPEN", "label": "늦게까지 영업" } */
    public record ConditionView(RequestCondition code, String label) {
    }
}
