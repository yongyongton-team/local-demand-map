-- 용용톤 Category 초기 데이터
-- 기존 데모 data.json에 정의된 38개 업종 등록
-- code: 서비스에서 사용하는 업종 식별 코드
-- name: 사용자 화면에 표시할 업종명
-- sbiz_name: 공공데이터 상가정보의 업종명
-- mart_check: 대형마트 대체 여부 확인 대상

INSERT INTO category (code, name, sbiz_name, mart_check)
VALUES
    ('SUPERMARKET', '슈퍼마켓', '슈퍼마켓', TRUE),
    ('CONVENIENCE_STORE', '편의점', '편의점', FALSE),
    ('HARDWARE_STORE', '철물점', '철물/공구 소매업', FALSE),
    ('KOREAN_RESTAURANT', '밥집', '백반/한정식', FALSE),
    ('CHINESE_RESTAURANT', '중국집', '중국집', FALSE),
    ('ACADEMY', '입시·교과 학원', '입시·교과학원', FALSE),
    ('FRUIT_VEG', '과일·채소 가게', '채소/과일 소매업', TRUE),
    ('CHICKEN', '치킨', '치킨', FALSE),
    ('LAUNDRY_SERVICE', '세탁소', '세탁소', FALSE),
    ('ART_ACADEMY', '미술학원', '미술학원', FALSE),
    ('BUTCHER', '정육점', '정육점', TRUE),
    ('HAIR_SALON', '미용실', '미용실', FALSE),
    ('CAFE', '카페', '카페', FALSE),
    ('SELF_LAUNDRY', '빨래방', '셀프 빨래방', FALSE),
    ('SNACK_BAR', '분식집', '김밥/만두/분식', FALSE),
    ('STUDY_CAFE', '스터디 카페', '독서실/스터디 카페', FALSE),
    ('GYM', '헬스장', '헬스장', FALSE),
    ('SIDE_DISH', '반찬가게', '반찬/식료품 소매업', TRUE),
    ('MUSIC_ACADEMY', '음악학원', '음악학원', FALSE),
    ('RICE_CAKE', '떡집', '떡/한과', FALSE),
    ('PHARMACY', '약국', '약국', FALSE),
    ('KOREAN_CLINIC', '한의원', '한의원', FALSE),
    ('YOGA_PILATES', '요가·필라테스', '요가/필라테스 학원', FALSE),
    ('DENTAL_CLINIC', '치과의원', '치과의원', FALSE),
    ('PET_SUPPLIES', '반려동물용품 가게', '애완동물/애완용품 소매업', FALSE),
    ('CLOTHING_REPAIR', '옷 수선', '의류/이불 수선업', FALSE),
    ('SEAFOOD_STORE', '생선가게', '수산물 소매업', TRUE),
    ('BAKERY', '빵집', '빵/도넛', FALSE),
    ('PIZZA', '피자', '피자', FALSE),
    ('FLOWER_SHOP', '꽃집', '꽃집', FALSE),
    ('STATIONERY_STORE', '문구점', '문구/회화용품 소매업', FALSE),
    ('OPTICAL_STORE', '안경점', '안경렌즈 소매업', FALSE),
    ('MARTIAL_ARTS', '태권도장', '태권도/무술학원', FALSE),
    ('VET_CLINIC', '동물병원', '동물병원', FALSE),
    ('ENT_CLINIC', '이비인후과 의원', '이비인후과 의원', FALSE),
    ('INTERNAL_PEDIATRIC_CLINIC', '내과·소아과', '내과/소아과 의원', FALSE),
    ('EYE_CLINIC', '안과 의원', '안과 의원', FALSE),
    ('BOOKSTORE', '서점', '서점', FALSE);
