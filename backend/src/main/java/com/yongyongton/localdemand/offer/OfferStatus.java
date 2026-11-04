package com.yongyongton.localdemand.offer;

/** 선주문 수령 제안 상태 (명세 3. 값 목록의 offer.status). */
public enum OfferStatus {
    OPEN,      // 주문 받는 중
    CONFIRMED, // 최소 주문 수를 채움
    CLOSED,    // 마감됐는데 못 채움
    CANCELLED, // 사장님이 취소
    DONE       // 수령 끝
}
