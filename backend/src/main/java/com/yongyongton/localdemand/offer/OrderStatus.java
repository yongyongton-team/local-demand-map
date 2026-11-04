package com.yongyongton.localdemand.offer;

/** 선주문 상태 (명세 3. 값 목록의 order.status). */
public enum OrderStatus {
    RESERVED,  // 선주문함
    CANCELLED, // 취소
    PICKED_UP  // 받아 감
}
