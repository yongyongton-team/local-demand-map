/**
 * AI 호출 (명세 6). 요청 문장 정리와 요약 문장 두 곳에만 써요.
 * AI 키는 서버 환경변수(AI_API_KEY)에만 두고, 주민 문장·AI 응답 본문은 로그에 남기지 않아요.
 * 요약 문장 속 숫자가 넣은 숫자에 없으면 버리고 기본 문장(by: TEMPLATE)을 써요.
 */
package com.yongyongton.localdemand.ai;
