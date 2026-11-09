package com.yongyongton.localdemand.dong;

/**
 * 공공데이터 기대치 모델이 본 판정 (V8 dong_category_estimate.verdict).
 * 라벨은 화면과 AI 설명에 그대로 써요. 숫자를 넣지 않아요(AI 문장 숫자 검사에 걸리지 않게).
 */
public enum EstimateVerdict {
    SHORT("걸어서 갈 거리로 봐도 부족해요"),
    SOME_NEARBY("동 안은 부족하고, 걸어서 갈 거리에 조금 있어요"),
    NEXT_DONG("동 안은 부족하지만, 옆 동네에는 있어요"),
    // 기대치보다 조금 적어도 우연으로 볼 수 있는 정도면 여기예요. "부족하지 않다"고 단정하지 않아요
    NOT_SHORT("인구에 비해 뚜렷하게 부족하지는 않아요"),
    NOT_JUDGED("이 업종은 모델을 믿기 어려워 판정하지 않아요");

    private final String label;

    EstimateVerdict(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 판정한 업종인지. NOT_JUDGED 면 기대치를 근거로 쓰지 않아요. */
    public boolean judged() {
        return this != NOT_JUDGED;
    }
}
