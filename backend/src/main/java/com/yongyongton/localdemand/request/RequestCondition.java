package com.yongyongton.localdemand.request;

/**
 * 주민이 원하는 조건 (명세 3. 값 목록의 condition). 목록을 고정해야 동네별로 셀 수 있어요.
 * 이름이 java.util.concurrent.locks.Condition 과 겹치지 않게 RequestCondition 으로 지었어요.
 * label 은 화면·AI에 보여 주는 이름이에요(POST /requests/parse, GET /owner/demand/{dongId}).
 */
public enum RequestCondition {
    LATE_OPEN("늦게까지 영업"),
    MORNING("아침 영업"),
    SMALL_QTY("낱개 구매"),
    DELIVERY("배달"),
    WEEKEND_OPEN("주말 영업"),
    CHEAP("저렴한 가격");

    private final String label;

    RequestCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
