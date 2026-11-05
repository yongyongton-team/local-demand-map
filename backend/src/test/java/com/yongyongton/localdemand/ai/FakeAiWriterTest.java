package com.yongyongton.localdemand.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yongyongton.localdemand.ai.AiWriter.CategoryOption;
import com.yongyongton.localdemand.ai.AiWriter.ConditionOption;
import com.yongyongton.localdemand.ai.AiWriter.ParsedRequest;
import com.yongyongton.localdemand.request.RequestCondition;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeAiWriterTest {

    private static final List<CategoryOption> CATEGORIES = List.of(
            new CategoryOption("FRUIT_VEG", "과일·채소 가게", "채소/과일 소매업"),
            new CategoryOption("BAKERY", "빵집", "제과점업"),
            new CategoryOption("SIDE_DISH", "반찬가게", "반찬/식료품 소매업"));
    private static final List<ConditionOption> CONDITIONS = Arrays.stream(RequestCondition.values())
            .map(c -> new ConditionOption(c.name(), c.label()))
            .toList();

    private final FakeAiWriter writer = new FakeAiWriter();

    @Test
    @DisplayName("업종 이름 낱말과 조건 낱말이 문장에 있으면 골라요")
    void matchWords() {
        ParsedRequest parsed = writer.parseRequest("밤 10시까지 하는 과일가게요. 낱개로 살 수 있으면 좋겠어요.",
                CATEGORIES, CONDITIONS);
        assertThat(parsed.categoryCode()).isEqualTo("FRUIT_VEG");
        assertThat(parsed.conditions()).containsExactly("LATE_OPEN", "SMALL_QTY");
        assertThat(parsed.candidateCodes()).isEmpty();

        assertThat(writer.parseRequest("빵 집이 생겼으면 해요", CATEGORIES, CONDITIONS).categoryCode()).isEqualTo("BAKERY");
    }

    @Test
    @DisplayName("못 찾으면 업종은 비우고, 같은 점수가 여럿이면 후보로 줘요")
    void noMatchOrTie() {
        ParsedRequest none = writer.parseRequest("아무거나 좋아요", CATEGORIES, CONDITIONS);
        assertThat(none.categoryCode()).isEmpty();
        assertThat(none.candidateCodes()).isEmpty();

        ParsedRequest tie = writer.parseRequest("과일이랑 반찬 파는 곳", CATEGORIES, CONDITIONS);
        assertThat(tie.categoryCode()).isEmpty();
        assertThat(tie.candidateCodes()).containsExactly("FRUIT_VEG", "SIDE_DISH");
    }

    @Test
    @DisplayName("요약 문장은 쓰지 않아요 → 기본 문장으로 가요")
    void noSummary() {
        assertThatThrownBy(() -> writer.writeSummary(SummaryKind.OWNER_DEMAND, Map.of()))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(writer.by()).isEqualTo("KEYWORD");
    }
}
