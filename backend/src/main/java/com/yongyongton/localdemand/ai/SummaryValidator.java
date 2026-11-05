package com.yongyongton.localdemand.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 요약 문장 검사 (명세 6-2). 하나라도 걸리면 그 문장은 버리고 기본 문장을 써요.
 * <ul>
 *   <li>문장 속 숫자가 넣은 자료(facts)에 없으면 버려요. 자료의 이름(예: 영덕1동)을 그대로 쓴 건 괜찮아요</li>
 *   <li>0과 1 사이 비율(예: 0.64)은 퍼센트(64)로 써도 돼요</li>
 *   <li>"확실히·반드시·무조건·보장" 같은 단정 표현이 있으면 버려요</li>
 * </ul>
 * 한계: "열네 명"처럼 한글로 쓴 숫자는 잡지 못해요. 지시문에서 아라비아 숫자만 쓰라고 해 둬요.
 */
final class SummaryValidator {

    static final int MAX_LENGTH = 300;
    static final List<String> BANNED_WORDS = List.of("확실", "반드시", "무조건", "보장", "틀림없", "대박", "성공할");

    /** 1,234 / 1.8 / 30 같은 숫자. */
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");

    private SummaryValidator() {
    }

    static boolean isValid(String text, Map<String, Object> facts) {
        if (text == null || text.isBlank() || text.length() > MAX_LENGTH) {
            return false;
        }
        if (BANNED_WORDS.stream().anyMatch(text::contains)) {
            return false;
        }
        Set<String> allowed = new HashSet<>();
        List<String> names = new ArrayList<>();
        collect(facts, allowed, names);

        // 이름(예: 영덕1동)은 지우고 나서 숫자를 봐요. 이름 속 1 때문에 "1명"이 통과하지 않게요
        String rest = text;
        for (String name : names.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            rest = rest.replace(name, " ");
        }
        Matcher m = NUMBER.matcher(rest);
        while (m.find()) {
            if (!allowed.contains(normalize(m.group()))) {
                return false;
            }
        }
        return true;
    }

    /** 자료 안의 숫자와 이름(글자 값)을 모아요. */
    private static void collect(Object value, Set<String> allowed, List<String> names) {
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> collect(v, allowed, names));
        } else if (value instanceof Collection<?> list) {
            list.forEach(v -> collect(v, allowed, names));
        } else if (value instanceof Number number) {
            BigDecimal n = new BigDecimal(number.toString());
            allowed.add(normalize(n.toPlainString()));
            if (n.signum() > 0 && n.compareTo(BigDecimal.ONE) < 0) {
                allowed.add(n.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString());
            }
        } else if (value instanceof CharSequence text && !text.isEmpty()) {
            names.add(text.toString());
        }
    }

    /** "1,234" → "1234", "30.0" → "30", "2.40" → "2.4" */
    static String normalize(String number) {
        return new BigDecimal(number.replace(",", "")).stripTrailingZeros().toPlainString();
    }
}
