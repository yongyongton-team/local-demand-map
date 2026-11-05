"""경기도 행정동으로 업종별 기대치 모델을 학습하고 용인 행정동의 부족/과밀 업종을 뽑는다.

사용: python3 gap.py <pop_age_utf8.csv> <households_utf8.csv> <store_counts.csv> <출력 폴더>
"""
import csv
import math
import random
import sys
from collections import defaultdict

import features
import poisson

pop_path, hh_path, store_path, out_dir = sys.argv[1:5]

pop = features.load_population(pop_path)
hh = features.load_households(hh_path)

# 생활밀착 업종: 집 근처에서 쓰는 가게 (소분류명 그대로)
TARGETS = sys.argv[5].split("|") if len(sys.argv) > 5 else None

counts = defaultdict(dict)  # 소분류 -> {행정동코드: 수}
labels = {}
with open(store_path, encoding="utf-8") as f:
    for row in csv.DictReader(f):
        counts[row["소분류"]][row["행정동코드"]] = int(row["가게수"])
        labels[row["소분류"]] = (row["대분류"], row["중분류"])

codes = [c for c, v in pop.items() if v["sido"] == "경기도" and c in hh]
yongin = [c for c in codes if pop[c]["sigungu"].startswith("용인시")]
train = [c for c in codes if c not in set(yongin)]

FEATS = ["age0_9", "age10_19", "age20_29", "age30_39", "age40_49", "age50_64", "age65p", "one_hh", "hh_size"]


def raw_x(c):
    p = pop[c]
    total_hh, one = hh[c]
    v = dict(p)
    v["one_hh"] = one
    v["hh_size"] = p["pop"] / total_hh
    return [v[k] for k in FEATS]


raw = {c: raw_x(c) for c in codes}
mean = [sum(raw[c][i] for c in train) / len(train) for i in range(len(FEATS))]
sd = [math.sqrt(sum((raw[c][i] - mean[i]) ** 2 for c in train) / len(train)) or 1.0 for i in range(len(FEATS))]
X = {c: [(raw[c][i] - mean[i]) / sd[i] for i in range(len(FEATS))] for c in codes}
OFF = {c: math.log(pop[c]["pop"]) for c in codes}

subs = TARGETS or sorted(counts)
random.seed(7)
folds = list(train)
random.shuffle(folds)
K = 5

summary = []
rows = []
for sub in subs:
    y = {c: counts.get(sub, {}).get(c, 0) for c in codes}
    if sum(y[c] for c in train) < 30:
        continue
    # 5겹 교차검증: 모델 vs 인구 비례(1인당 평균) 기준선
    dev_m = dev_b = 0.0
    abs_m = abs_b = 0.0
    for k in range(K):
        test = set(folds[k::K])
        tr = [c for c in train if c not in test]
        beta = poisson.fit([X[c] for c in tr], [y[c] for c in tr], [OFF[c] for c in tr])
        rate = sum(y[c] for c in tr) / sum(pop[c]["pop"] for c in tr)
        for c in test:
            mu = poisson.predict(beta, X[c], OFF[c])
            mb = rate * pop[c]["pop"]
            dev_m += poisson.deviance(y[c], mu)
            dev_b += poisson.deviance(y[c], mb)
            abs_m += abs(y[c] - mu)
            abs_b += abs(y[c] - mb)
    beta = poisson.fit([X[c] for c in train], [y[c] for c in train], [OFF[c] for c in train])
    gain = 1 - dev_m / dev_b if dev_b else 0.0
    summary.append([sub, *labels.get(sub, ("", "")), sum(y[c] for c in train), sum(y[c] for c in yongin),
                    round(gain, 3), round(abs_m / len(train), 2), round(abs_b / len(train), 2)])
    for c in yongin:
        mu = poisson.predict(beta, X[c], OFF[c])
        actual = y[c]
        p_low = poisson.poisson_cdf(actual, mu)
        p_high = 1 - poisson.poisson_cdf(actual - 1, mu) if actual > 0 else 1.0
        rows.append([pop[c]["sigungu"], pop[c]["dong"], c, sub, round(mu, 1), actual, round(actual - mu, 1),
                     round(p_low, 4), round(p_high, 4), pop[c]["pop"]])

with open(f"{out_dir}/model_summary.csv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["업종", "대분류", "중분류", "경기 학습 가게수", "용인 가게수", "교차검증 개선율", "모델 평균오차", "기준선 평균오차"])
    w.writerows(sorted(summary, key=lambda r: -r[5]))
with open(f"{out_dir}/yongin_gap.csv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["구", "행정동", "행정동코드", "업종", "기대치", "실제", "차이", "이보다적을확률", "이보다많을확률", "인구"])
    w.writerows(rows)
print("학습 동", len(train), "용인 동", len(yongin), "업종", len(summary))
