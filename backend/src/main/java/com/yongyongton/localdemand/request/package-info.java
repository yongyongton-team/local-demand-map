/**
 * 주민 요청 (명세 5-3).
 * POST /requests/parse, POST /requests, GET /me/requests, DELETE /requests/{id}.
 * 같은 동·같은 업종 중복은 DB 고유 제약 uk_request_user_dong_category 가 막아요 → ALREADY_REQUESTED.
 */
package com.yongyongton.localdemand.request;
