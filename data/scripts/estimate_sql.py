"""기대치 모델 결과 전체(39개 동 × 38개 업종)로 서버 DB 시드 SQL(Flyway)을 만든다.

창업자 리포트가 "인구 구성으로 보면 보통 몇 곳인데 실제는 몇 곳"을 근거로 쓰게 하려고 넣는다.
SQL 파일을 직접 고치지 말고, 결과 CSV가 바뀌면 이 스크립트를 다시 돌린다.

사용: python3 estimate_sql.py <yongin_dong_category_gap.csv> <출력 sql>
예:   python3 estimate_sql.py ../results/yongin_dong_category_gap.csv \
          ../../backend/src/main/resources/db/migration/V8__seed_dong_category_estimate.sql

판정(verdict)
- NOT_JUDGED: 교차검증 개선율이 0.15 미만인 업종(모델을 믿기 어려워 판정하지 않음)
- SHORT / SOME_NEARBY / NEXT_DONG: 동 경계로 본 부족 후보를 반경으로 다시 본 결과
  (radius.py 의 "반경 안에도 부족" / "반경 안에 일부" / "옆 동네에 있음")
- NOT_SHORT: 그 밖(인구에 비해 부족하지 않음)
"""
import csv
import sys

GAIN_MIN = 0.15
RADIUS_VERDICT = {
    "반경 안에도 부족": "SHORT",
    "반경 안에 일부": "SOME_NEARBY",
    "옆 동네에 있음": "NEXT_DONG",
}

src, dst = sys.argv[1:3]
rows = list(csv.DictReader(open(src, encoding="utf-8-sig")))


def verdict(r):
    if float(r["모델 개선율"]) < GAIN_MIN:
        return "NOT_JUDGED"
    return RADIUS_VERDICT.get(r["반경 판정(1.5km)"], "NOT_SHORT")


def text(s):
    return "'" + s.replace("'", "''") + "'"


def num_or_null(s):
    return s if s != "" else "NULL"


values = []
for r in rows:
    values.append("({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})".format(
        text(r["구"].replace("용인시 ", "")), text(r["행정동"]), text(r["업종"]),
        r["모델 개선율"], r["기대치"], r["동 안 실제"], r["이보다적을확률"],
        num_or_null(r["반경1km 안"]), num_or_null(r["반경1.5km 안"]), text(verdict(r)), r["인구"]))

counts = {}
for r in rows:
    counts[verdict(r)] = counts.get(verdict(r), 0) + 1

with open(dst, "w", encoding="utf-8") as f:
    f.write(f"""-- 기대치 모델 결과 전체({len(rows):,}쌍 = 39개 동 × 38개 업종)와 동별 주민등록 인구
-- 출처: data/results/yongin_dong_category_gap.csv (모델 설명: data/README.md)
-- 만든 법: data/scripts/estimate_sql.py 로 만들었어요. 직접 고치지 말고 스크립트를 다시 돌려요.
-- 판정 수: {", ".join(f"{k} {v}" for k, v in sorted(counts.items()))}

ALTER TABLE dong ADD COLUMN population INT NULL COMMENT '주민등록 인구(2026-09)';

CREATE TABLE dong_category_estimate (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    dong_id            BIGINT      NOT NULL,
    category_id        BIGINT      NOT NULL,
    model_gain         DOUBLE      NOT NULL COMMENT '업종별 교차검증 개선율. 0.15 미만이면 판정하지 않아요',
    expected_count     DOUBLE      NOT NULL COMMENT '인구 구성(나이대·1인 가구·세대원 수)으로 본 보통 가게 수',
    dong_store_count   INT         NOT NULL COMMENT '동 경계 안 실제 가게 수',
    prob_fewer         DOUBLE      NOT NULL COMMENT '기대치가 맞다면 실제가 이만큼 적을 확률',
    radius_1km_count   INT         NULL     COMMENT '부족 후보만: 동 중심 1km 안 같은 업종 가게 수',
    radius_1_5km_count INT         NULL     COMMENT '부족 후보만: 동 중심 1.5km 안 같은 업종 가게 수',
    verdict            VARCHAR(20) NOT NULL COMMENT 'SHORT / SOME_NEARBY / NEXT_DONG / NOT_SHORT / NOT_JUDGED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_estimate_dong_category (dong_id, category_id),
    CONSTRAINT fk_estimate_dong FOREIGN KEY (dong_id) REFERENCES dong (id),
    CONSTRAINT fk_estimate_category FOREIGN KEY (category_id) REFERENCES category (id)
);

CREATE TEMPORARY TABLE estimate_seed (
    gu                 VARCHAR(10) NOT NULL,
    dong_name          VARCHAR(30) NOT NULL,
    sbiz_name          VARCHAR(50) NOT NULL,
    model_gain         DOUBLE      NOT NULL,
    expected_count     DOUBLE      NOT NULL,
    dong_store_count   INT         NOT NULL,
    prob_fewer         DOUBLE      NOT NULL,
    radius_1km_count   INT         NULL,
    radius_1_5km_count INT         NULL,
    verdict            VARCHAR(20) NOT NULL,
    population         INT         NOT NULL
);

INSERT INTO estimate_seed VALUES
""")
    f.write(",\n".join(values))
    f.write(""";

INSERT INTO dong_category_estimate (dong_id, category_id, model_gain, expected_count, dong_store_count, prob_fewer,
                                    radius_1km_count, radius_1_5km_count, verdict)
SELECT d.id, c.id, s.model_gain, s.expected_count, s.dong_store_count, s.prob_fewer,
       s.radius_1km_count, s.radius_1_5km_count, s.verdict
FROM estimate_seed s
         JOIN dong d ON d.name = s.dong_name AND d.gu = s.gu
         JOIN category c ON c.sbiz_name = s.sbiz_name;

UPDATE dong d
    JOIN (SELECT DISTINCT gu, dong_name, population FROM estimate_seed) p ON d.name = p.dong_name AND d.gu = p.gu
SET d.population = p.population;

DROP TEMPORARY TABLE estimate_seed;
""")

print(f"{dst}: {len(rows)}쌍, {counts}")
