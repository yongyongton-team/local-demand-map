package com.yongyongton.localdemand.request;

/** 갈 수 있는 때 (명세 3. 값 목록). 한 요청에 여러 개 고를 수 있어요. label 은 요약 문장에 써요. */
public enum TimeSlot {
    WEEKDAY_DAY("평일 낮"),
    WEEKDAY_EVENING("평일 저녁"),
    WEEKEND("주말");

    private final String label;

    TimeSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
