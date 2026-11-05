"""상가(상권)정보 경기도 파일에서 행정동 x 업종(소분류) 가게 수를 센다.

사용: python3 extract_stores.py <경기도 csv> <출력 csv>
"""
import csv
import sys
from collections import Counter

src, dst = sys.argv[1], sys.argv[2]
counts = Counter()
names = {}
with open(src, encoding="utf-8-sig", newline="") as f:
    r = csv.reader(f)
    header = next(r)
    idx = {h.strip(): i for i, h in enumerate(header)}
    need = ["행정동코드", "행정동명", "상권업종대분류명", "상권업종중분류명", "상권업종소분류명"]
    missing = [n for n in need if n not in idx]
    if missing:
        print("없는 열:", missing, "\n열 목록:", header)
        sys.exit(1)
    for row in r:
        # 상가정보는 8자리, 주민등록 인구는 10자리 행정기관코드 → 뒤에 "00"
        code = row[idx["행정동코드"]].strip().ljust(10, "0")
        sub = row[idx["상권업종소분류명"]].strip()
        counts[(code, sub)] += 1
        names[sub] = (row[idx["상권업종대분류명"]].strip(), row[idx["상권업종중분류명"]].strip())

with open(dst, "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["행정동코드", "대분류", "중분류", "소분류", "가게수"])
    for (code, sub), n in sorted(counts.items()):
        big, mid = names[sub]
        w.writerow([code, big, mid, sub, n])
print("행정동 x 업종 조합:", len(counts), "가게 수 합:", sum(counts.values()))
