package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.dong.Dong;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 좌표(위도·경도)가 어느 동 경계 안에 있는지 찾아요. PUT /me/shop 에서 가게 동을 넣을 때 써요.
 * dong.boundary 는 GeoJSON 문자열이에요. Feature / FeatureCollection / Polygon / MultiPolygon 모두 읽어요.
 * GeoJSON 좌표 순서는 [경도, 위도]예요.
 */
@Component
public class DongLocator {

    private static final Logger log = LoggerFactory.getLogger(DongLocator.class);

    private final EntityManager em;
    private final ObjectMapper objectMapper;

    public DongLocator(EntityManager em, ObjectMapper objectMapper) {
        this.em = em;
        this.objectMapper = objectMapper;
    }

    /** 어느 동에도 안 들어가면(용인시 밖, 또는 경계 데이터가 아직 없음) 빈 값. */
    public Optional<Long> findDongId(double lat, double lon) {
        List<Dong> dongs = em.createQuery("SELECT d FROM Dong d WHERE d.boundary IS NOT NULL", Dong.class)
                .getResultList();
        for (Dong dong : dongs) {
            JsonNode geo;
            try {
                geo = objectMapper.readTree(dong.getBoundary());
            } catch (RuntimeException e) {
                log.warn("동 경계 GeoJSON을 읽지 못했어요. dongId={}", dong.getId());
                continue;
            }
            if (contains(geo, lon, lat)) {
                return Optional.of(dong.getId());
            }
        }
        return Optional.empty();
    }

    /** GeoJSON 도형 안에 점(경도 x, 위도 y)이 있는지. */
    static boolean contains(JsonNode geo, double x, double y) {
        if (geo == null || geo.isNull()) {
            return false;
        }
        String type = geo.path("type").asString("");
        switch (type) {
            case "FeatureCollection":
                for (JsonNode feature : geo.path("features")) {
                    if (contains(feature, x, y)) {
                        return true;
                    }
                }
                return false;
            case "Feature":
                return contains(geo.path("geometry"), x, y);
            case "Polygon":
                return inPolygon(geo.path("coordinates"), x, y);
            case "MultiPolygon":
                for (JsonNode polygon : geo.path("coordinates")) {
                    if (inPolygon(polygon, x, y)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    /** 첫 고리(바깥 경계) 안에 있고, 나머지 고리(구멍) 안에는 없어야 해요. */
    private static boolean inPolygon(JsonNode rings, double x, double y) {
        if (!rings.isArray() || rings.isEmpty() || !inRing(rings.get(0), x, y)) {
            return false;
        }
        for (int i = 1; i < rings.size(); i++) {
            if (inRing(rings.get(i), x, y)) {
                return false;
            }
        }
        return true;
    }

    /** 점에서 오른쪽으로 선을 그었을 때 경계를 홀수 번 지나면 안쪽이에요. */
    private static boolean inRing(JsonNode ring, double x, double y) {
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring.get(i).get(0).asDouble();
            double yi = ring.get(i).get(1).asDouble();
            double xj = ring.get(j).get(0).asDouble();
            double yj = ring.get(j).get(1).asDouble();
            boolean crosses = (yi > y) != (yj > y)
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }
}
