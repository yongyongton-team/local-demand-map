-- 통계 모델의 최종 부족 업종 11개
-- 출처: data/results/robust_shortages.csv
-- MISSING = 기대 점포 수 대비 공급 부족
-- 반경 1km 점포 수와 가장 가까운 마트 정보를 저장

INSERT INTO gap (
    dong_id,
    category_id,
    status,
    walk_shop_count,
    nearest_mart_id,
    nearest_mart_km
)
SELECT
    d.id,
    c.id,
    'MISSING',
    v.walk_shop_count,
    m.id,
    v.nearest_mart_km
FROM (
         SELECT '4146126200' AS adm_code, '미용실' AS sbiz_name,
                18 AS walk_shop_count, '정동마트' AS mart_name,
                5.76 AS nearest_mart_km
         UNION ALL SELECT '4146354000', '미용실', 8, '코스트코 공세점', 0.68
         UNION ALL SELECT '4146356000', '채소/과일 소매업', 0, '이마트 동백점', 2.43
         UNION ALL SELECT '4146134000', '미용실', 2, '정동마트', 12.26
         UNION ALL SELECT '4146125300', '미용실', 23, '정동마트', 9.81
         UNION ALL SELECT '4146354000', '입시·교과학원', 5, '코스트코 공세점', 0.68
         UNION ALL SELECT '4146153600', '요가/필라테스 학원', 5, '정동마트', 0.14
         UNION ALL SELECT '4146126200', '중국집', 4, '정동마트', 5.76
         UNION ALL SELECT '4146125900', '미용실', 1, '이마트 용인점', 10.20
         UNION ALL SELECT '4146351600', '애완동물/애완용품 소매업', 1, '이마트 흥덕점', 0.10
         UNION ALL SELECT '4146134000', '치킨', 1, '정동마트', 12.26
     ) AS v
         JOIN dong d ON d.adm_code = v.adm_code
         JOIN category c ON c.sbiz_name = v.sbiz_name
         JOIN mart m ON m.name = v.mart_name;
