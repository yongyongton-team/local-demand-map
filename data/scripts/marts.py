# 용인시 대규모 점포 현황 중 대형마트 12곳의 확인된 좌표를 저장하고,
# 동네 중심에서 가장 가까운 대형마트 거리를 계산해 식품 업종 빈자리와 겹쳐 본다.
import csv
import json
import math
import sys

data_json, out_csv, out_json = sys.argv[1], sys.argv[2], sys.argv[3]
D = json.load(open(data_json, encoding="utf-8"))

# OpenStreetMap에서 찾은 좌표. 확인 = 번지 또는 매장 이름까지 맞은 결과
MARTS = [
    ("(주)이마트 트레이더스 구성점", "용구대로 2457", 37.3049951, 127.1050844, "번지 일치"),
    ("(주)농협유통 하나로클럽 용인점", "중부대로 682", 37.2659895, 127.1358468, "옆 번지(중부대로684번길) 매장"),
    ("(주)이마트 수지점", "수지로 203", 37.3197263, 127.0833962, "번지 일치"),
    ("(주)이마트 스타필드마켓 죽전점", "포은대로 552", 37.3254196, 127.1100313, "매장 이름 일치(이마트 죽전점)"),
    ("(주)이마트 용인점", "명지로 53", 37.2314331, 127.1891578, "매장 이름 일치"),
    ("(주)이마트 동백점", "동백죽전대로 444", 37.2779189, 127.1518123, "번지 일치"),
    ("(주)이마트 보라점", "한보라1로 92", 37.2475958, 127.1051043, "매장 이름 일치"),
    ("(주)이마트 흥덕점", "흥덕중앙로 60", 37.2757427, 127.0733200, "번지 일치"),
    ("롯데마트 신갈점", "중부대로 375", 37.2719761, 127.1082491, "매장 이름 일치"),
    ("코스트코 공세점", "탑실로 38", 37.2382278, 127.1088291, "번지 일치"),
    ("정동마트", "고림로 206", 37.2495919, 127.2231379, "매장 이름 일치"),
    ("느티나무마트", "수지로 29", 37.3060066, 127.0742664, "매장 이름 일치"),
]
# 대형마트가 대신할 수 있는 식품 업종
SUBST = ["슈퍼마켓", "채소/과일 소매업", "정육점", "수산물 소매업", "반찬/식료품 소매업"]
K = math.cos(math.radians(37.2))


def km(a, b, c, d):
    return math.hypot((a - c) * 111, (b - d) * 111 * K)


def inside(lon, lat, rings):
    c = False
    for r in rings:
        for i in range(len(r)):
            x1, y1 = r[i]
            x2, y2 = r[i - 1]
            if (y1 > lat) != (y2 > lat) and lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
                c = not c
    return c


def dong_of(lat, lon):
    return next((d["name"] for d in D["dongs"] if inside(lon, lat, d["rings"])), "")


with open(out_csv, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(["상호명", "도로명주소", "위도", "경도", "행정동(경계 기준)", "좌표 확인"])
    for name, road, lat, lon, how in MARTS:
        w.writerow([name, "경기도 용인시 " + road, lat, lon, dong_of(lat, lon), how])

cats = [c["name"] for c in D["cats"]]


def judge(di, ci, radius):
    g, cat = D["gaps"][di][ci], D["cats"][ci]
    if not g or cat["gain"] < 0.15:
        return "na"
    e, a, r1, r15, pl, ph = g
    if e >= 3 and pl <= 0.01:
        if not radius:
            return "short"
        if r1 < 0.5 * e and r15 < 0.5 * e:
            return "short"
        return "part" if r15 < e else "near"
    return "ok"


near = []
for di, d in enumerate(D["dongs"]):
    best = min(((km(d["lat"], d["lon"], lat, lon), name) for name, _, lat, lon, _ in MARTS))
    near.append({"km": round(best[0], 2), "name": best[1]})
print("동네별 가장 가까운 대형마트 (1.5km 안만)")
for d, n in zip(D["dongs"], near):
    if n["km"] <= 1.5:
        print(f"  {d['name']}: {n['name']} {n['km']}km")
print("\n식품 업종 빈자리와 겹침")
for radius in (False, True):
    tot = hit = 0
    for di, d in enumerate(D["dongs"]):
        for c in SUBST:
            ci = cats.index(c)
            if judge(di, ci, radius) == "short":
                tot += 1
                if near[di]["km"] <= 1.5:
                    hit += 1
                    print(f"  {'반경' if radius else '행정동'} 기준 {d['name']} {c}: {near[di]['name']} {near[di]['km']}km")
    print(f"  -> {'반경 1.5km' if radius else '행정동'} 기준 식품 빈자리 {tot}개 중 대형마트 1.5km 안 {hit}개")
json.dump({"marts": [{"name": n, "lat": la, "lon": lo} for n, _, la, lo, _ in MARTS], "subst": SUBST,
           "nearMart": near}, open(out_json, "w", encoding="utf-8"), ensure_ascii=False)
