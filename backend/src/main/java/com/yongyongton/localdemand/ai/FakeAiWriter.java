package com.yongyongton.localdemand.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 키 없이 쓰는 대신 정리기(AI_PROVIDER=fake, 기본값). <b>AI가 아니에요.</b>
 * 업종 이름·조건 낱말이 문장에 그대로 있는지만 봐요. 개발할 때와 AI가 안 될 때 시연 예비용이에요.
 * 요약 문장은 쓰지 않아서 서버의 기본 문장(by: TEMPLATE)이 나가요.
 */
public class FakeAiWriter implements AiWriter {

    /** 업종 이름에서 빼고 보는 흔한 낱말. "과일·채소 가게" → 과일, 채소 */
    private static final Set<String> COMMON_WORDS = Set.of("가게", "소매업", "전문점", "판매업", "서비스업", "및", "기타");

    /** 조건 코드마다 문장에서 찾는 낱말. */
    private static final Map<String, List<String>> CONDITION_WORDS = new LinkedHashMap<>();

    static {
        CONDITION_WORDS.put("LATE_OPEN", List.of("늦게", "밤", "야간", "심야", "24시"));
        CONDITION_WORDS.put("MORNING", List.of("아침", "오전", "일찍"));
        CONDITION_WORDS.put("SMALL_QTY", List.of("낱개", "소량", "조금씩", "하나씩", "1인분"));
        CONDITION_WORDS.put("DELIVERY", List.of("배달"));
        CONDITION_WORDS.put("WEEKEND_OPEN", List.of("주말", "토요일", "일요일"));
        CONDITION_WORDS.put("CHEAP", List.of("저렴", "싸게", "싼", "가성비"));
    }

    @Override
    public String by() {
        return "KEYWORD";
    }

    @Override
    public ParsedRequest parseRequest(String text, List<CategoryOption> categories, List<ConditionOption> conditions) {
        String compact = text.replace(" ", "");

        int best = 0;
        List<String> bestCodes = new ArrayList<>();
        for (CategoryOption c : categories) {
            int score = 0;
            for (String word : words(c)) {
                if (compact.contains(word)) {
                    score++;
                }
            }
            if (score > best) {
                best = score;
                bestCodes = new ArrayList<>(List.of(c.code()));
            } else if (score > 0 && score == best) {
                bestCodes.add(c.code());
            }
        }

        List<String> foundConditions = new ArrayList<>();
        for (ConditionOption c : conditions) {
            List<String> words = CONDITION_WORDS.getOrDefault(c.code(), List.of());
            if (words.stream().anyMatch(compact::contains)) {
                foundConditions.add(c.code());
            }
        }

        // 하나로 정해지면 그 업종, 여럿이 같은 점수면 못 정한 것으로 보고 후보로 줘요
        if (bestCodes.size() == 1) {
            return new ParsedRequest(bestCodes.get(0), foundConditions, List.of());
        }
        return new ParsedRequest("", foundConditions, bestCodes.stream().limit(3).toList());
    }

    @Override
    public String writeSummary(SummaryKind kind, Map<String, Object> facts) {
        throw new AiUnavailableException("FAKE_NO_SUMMARY");
    }

    /** 업종 이름과 상가정보 업종 이름을 낱말로 나눠요. 두 글자 미만과 흔한 낱말은 빼요. */
    private static List<String> words(CategoryOption c) {
        String all = (c.name() == null ? "" : c.name()) + " " + (c.sbizName() == null ? "" : c.sbizName());
        return Arrays.stream(all.split("[\\s·/,()]+"))
                .map(w -> w.endsWith("소매업") && w.length() > 3 ? w.substring(0, w.length() - 3) : w)
                .filter(w -> w.length() >= 2 && !COMMON_WORDS.contains(w))
                .distinct()
                .toList();
    }
}
