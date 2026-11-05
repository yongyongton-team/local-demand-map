package com.yongyongton.localdemand.ai;

import java.util.List;
import java.util.Map;

/**
 * AI가 하는 일 두 가지(명세 6). 결과는 "검증 전 그대로"라서, 부르는 쪽(서비스)이 꼭 검증해요.
 * 실패하면 {@link AiUnavailableException}을 던져요.
 * <ul>
 *   <li>{@link LlmAiWriter}: 진짜 AI 모델 (AI_PROVIDER=openai)</li>
 *   <li>{@link FakeAiWriter}: 키 없이 낱말 맞추기로 대신해요 (AI_PROVIDER=fake, 기본값). AI가 아니에요</li>
 * </ul>
 */
public interface AiWriter {

    /** 응답의 by 칸에 쓰는 이름. 진짜 AI면 "AI", 낱말 맞추기면 "KEYWORD". */
    String by();

    /**
     * 주민 문장 → 업종 코드·조건 코드 (POST /requests/parse).
     * 목록에 없는 코드가 섞여 있을 수 있어요. 버리는 건 부르는 쪽 몫이에요.
     */
    ParsedRequest parseRequest(String text, List<CategoryOption> categories, List<ConditionOption> conditions);

    /** 서버가 계산한 숫자(facts) → 요약 문장 (화면 5 summary, 화면 7 explanation). */
    String writeSummary(SummaryKind kind, Map<String, Object> facts);

    /** AI에 보여 주는 업종. code 만 돌려받아요. */
    record CategoryOption(String code, String name, String sbizName) {
    }

    /** AI에 보여 주는 조건. 예: LATE_OPEN / 늦게까지 영업 */
    record ConditionOption(String code, String label) {
    }

    /**
     * AI가 정리한 결과(검증 전).
     *
     * @param categoryCode   고른 업종 코드. 못 정하면 ""
     * @param conditions     조건 코드들
     * @param candidateCodes 업종을 못 정했을 때 고를 만한 업종 코드(최대 3개)
     */
    record ParsedRequest(String categoryCode, List<String> conditions, List<String> candidateCodes) {
    }
}
