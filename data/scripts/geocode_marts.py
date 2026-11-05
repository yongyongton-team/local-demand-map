# 용인시 대규모 점포 27곳의 도로명 주소로 좌표를 찾는다(OpenStreetMap Nominatim, 1초에 1번).
# 찾은 좌표가 데모 data.json 의 어느 용인 행정동 경계 안에 들어가는지도 같이 적는다.
import csv
import json
import sys
import time
import urllib.parse
import urllib.request

src, data_json, out = sys.argv[1], sys.argv[2], sys.argv[3]
D = json.load(open(data_json, encoding="utf-8"))


def inside(lon, lat, rings):
    c = False
    for r in rings:
        n = len(r)
        for i in range(n):
            x1, y1 = r[i]
            x2, y2 = r[i - 1]
            if (y1 > lat) != (y2 > lat) and lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
                c = not c
    return c


def dong_of(lat, lon):
    for d in D["dongs"]:
        if inside(lon, lat, d["rings"]):
            return d["name"]
    return ""


def search(q):
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(
        {"q": q, "format": "jsonv2", "limit": 1, "countrycodes": "kr"})
    req = urllib.request.Request(url, headers={"User-Agent": "yongyongton-demo/0.1 (hackathon research)"})
    with urllib.request.urlopen(req, timeout=20) as r:
        res = json.load(r)
    time.sleep(1.2)
    return (float(res[0]["lat"]), float(res[0]["lon"])) if res else None


rows = list(csv.DictReader(open(src, encoding="utf-8")))
with open(out, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(["업태", "상호명", "도로명주소", "위도", "경도", "행정동(경계 기준)", "찾은 방법"])
    for row in rows:
        road = row["소재지(사무실)"].split("(")[0].strip()
        hit, how = search(road), "도로명주소"
        if not hit:
            name = row["상호명"].replace("(주)", "").strip()
            hit, how = search(f"{name} 용인"), "상호명"
        lat, lon = hit if hit else ("", "")
        w.writerow([row["업태"], row["상호명"], road, lat, lon, dong_of(lat, lon) if hit else "", how if hit else "못 찾음"])
        print(row["상호명"], "->", lat, lon, dong_of(lat, lon) if hit else "", how if hit else "못 찾음")
