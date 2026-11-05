/**
 * AI 호출 (명세 6). 요청 문장 정리(POST /requests/parse)와 요약 문장(SummaryService) 두 곳에만 써요.
 * 화면 7 창업자 리포트의 explanation 도 SummaryService.write(SummaryKind.FOUNDER_REPORT, …)로 만들어요.
 * AI 키는 서버 환경변수(AI_API_KEY)에만 두고, 주민 문장·AI 응답 본문은 로그에 남기지 않아요.
 * 요약 문장 속 숫자가 넣은 숫자에 없으면 버리고 기본 문장(by: TEMPLATE)을 써요.
 */
package com.yongyongton.localdemand.ai;
