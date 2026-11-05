"""행정동 단위 '부족' 후보가 경계 효과인지 반경으로 다시 확인한다.

각 용인 행정동의 중심(그 동 가게 좌표의 중앙값)에서 반경 안의 같은 업종 가게 수를 센다.
경계 밖(수원·성남 등) 가게도 포함한다.

사용: python3 radius.py <경기 상가 csv> <yongin_gap.csv> <model_summary.csv> <출력 csv> [반경 km]
"""
import csv
import math
import statistics
import sys
from collections import defaultdict

src, gap_path, summary_path, dst = sys.argv[1:5]
RADIUS_KM = float(sys.argv[5]) if len(sys.argv) > 5 else 1.0
P_MAX, EXP_MIN, GAIN_MIN = 0.01, 3.0, 0.15

gain = {r["업종"]: float(r["교차검증 개선율"]) for r in csv.DictReader(open(summary_path, encoding="utf-8"))}
cands = [r for r in csv.DictReader(open(gap_path, encoding="utf-8"))
         if gain.get(r["업종"], 0) >= GAIN_MIN and float(r["기대치"]) >= EXP_MIN
         and float(r["이보다적을확률"]) <= P_MAX]
want = {r["업종"] for r in cands}

pts = defaultdict(list)       # 업종 -> [(lat, lon)]
dong_pts = defaultdict(list)  # 용인 행정동코드 -> [(lat, lon)]
with open(src, encoding="utf-8-sig", newline="") as f:
    r = csv.reader(f)
    h = {k: i for i, k in enumerate(next(r))}
    for row in r:
        try:
            lon, lat = float(row[h["경도"]]), float(row[h["위도"]])
        except ValueError:
            continue
        if not (126.9 <= lon <= 127.6 and 36.95 <= lat <= 37.5):
            continue
        sub = row[h["상권업종소분류명"]]
        if sub in want:
            pts[sub].append((lat, lon))
        if row[h["시군구명"]].startswith("용인시"):
            dong_pts[row[h["행정동코드"]].ljust(10, "0")].append((lat, lon))

center = {c: (statistics.median(p[0] for p in v), statistics.median(p[1] for p in v)) for c, v in dong_pts.items()}


def km(a, b):
    dlat = math.radians(b[0] - a[0])
    dlon = math.radians(b[1] - a[1])
    x = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlon / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(x))


out = []
for c in cands:
    ctr = center.get(c["행정동코드"])
    if not ctr:
        continue
    near = sum(1 for p in pts[c["업종"]] if km(ctr, p) <= RADIUS_KM)
    exp_ = float(c["기대치"])
    verdict = "반경 안에도 부족" if near < 0.5 * exp_ else ("반경 안에 일부" if near < exp_ else "옆 동네에 있음")
    out.append([c["구"], c["행정동"], c["업종"], c["기대치"], c["실제"], c["이보다적을확률"], near, verdict, c["인구"]])

out.sort(key=lambda r: (r[7] != "반경 안에도 부족", float(r[5])))
with open(dst, "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["구", "행정동", "업종", "기대치", "동 안 실제", "이보다적을확률", f"반경{RADIUS_KM:g}km 안", "판정", "인구"])
    w.writerows(out)
print("후보", len(cands), "판정", {v: sum(1 for r in out if r[7] == v) for v in ("반경 안에도 부족", "반경 안에 일부", "옆 동네에 있음")})
