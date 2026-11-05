package com.yongyongton.localdemand.common;

/** 기본 문장(TEMPLATE)을 만들 때 쓰는 한국어 도우미. */
public final class KoreanText {

    private KoreanText() {
    }

    /**
     * 받침에 맞는 조사를 붙여요. 예: josa("빵집", "을", "를") → "빵집을", josa("반찬가게", "을", "를") → "반찬가게를".
     * 마지막 글자가 한글이 아니면(숫자·영어) 받침 없는 쪽을 써요.
     */
    public static String josa(String word, String withFinal, String withoutFinal) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        char last = word.charAt(word.length() - 1);
        boolean hasFinal = last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
        return word + (hasFinal ? withFinal : withoutFinal);
    }
}
