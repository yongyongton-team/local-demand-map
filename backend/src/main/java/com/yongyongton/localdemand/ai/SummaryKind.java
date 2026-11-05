package com.yongyongton.localdemand.ai;

/** 요약 문장이 어느 화면 것인지 (ai_summary.kind, 20자 이내). */
public enum SummaryKind {
    OWNER_DEMAND,   // 화면 5 동네 요청 자세히의 summary
    FOUNDER_REPORT  // 화면 7 동네 리포트의 explanation
}
