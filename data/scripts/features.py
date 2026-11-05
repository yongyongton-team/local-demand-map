"""행정동별 인구 구성 특징을 만든다 (순수 파이썬)."""
import csv

AGE_BANDS = [(0, 9, "age0_9"), (10, 19, "age10_19"), (20, 29, "age20_29"), (30, 39, "age30_39"),
             (40, 49, "age40_49"), (50, 64, "age50_64"), (65, 200, "age65p")]


def load_population(path):
    """행정기관코드 -> {sido, sigungu, dong, pop, 연령대 비율}"""
    out = {}
    with open(path, encoding="utf-8") as f:
        r = csv.reader(f)
        header = next(r)
        idx = {h: i for i, h in enumerate(header)}
        age_cols = []
        for h, i in idx.items():
            if h.endswith("세남자") or h.endswith("세여자"):
                num = h.replace("세남자", "").replace("세여자", "").replace("이상", "")
                age_cols.append((int(num), i))
        for row in r:
            dong = row[idx["읍면동명"]].strip()
            if not dong:
                continue
            pop = int(row[idx["계"]].replace(",", "") or 0)
            if pop <= 0:
                continue
            bands = {name: 0 for _, _, name in AGE_BANDS}
            for age, i in age_cols:
                v = int(row[i].replace(",", "") or 0)
                for lo, hi, name in AGE_BANDS:
                    if lo <= age <= hi:
                        bands[name] += v
                        break
            rec = {"sido": row[idx["시도명"]], "sigungu": row[idx["시군구명"]], "dong": dong, "pop": pop}
            for name, v in bands.items():
                rec[name] = v / pop
            out[row[idx["행정기관코드"]]] = rec
    return out


def load_households(path):
    """행정기관코드 -> (전체 세대수, 1인 세대 비율)"""
    out = {}
    with open(path, encoding="utf-8") as f:
        r = csv.reader(f)
        header = next(r)
        idx = {h: i for i, h in enumerate(header)}
        for row in r:
            total = int(row[idx["전체세대수"]].replace(",", "") or 0)
            one = int(row[idx["1인세대"]].replace(",", "") or 0)
            if total > 0:
                out[row[idx["행정기관코드"]]] = (total, one / total)
    return out
