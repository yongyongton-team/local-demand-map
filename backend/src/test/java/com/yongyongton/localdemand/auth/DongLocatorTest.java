package com.yongyongton.localdemand.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 경계 안/밖 계산만 확인해요(DB 없이). 좌표는 [경도, 위도]. */
class DongLocatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    // 경도 127.10~127.12, 위도 37.26~37.28 네모
    private static final String SQUARE = "[[127.10,37.26],[127.12,37.26],[127.12,37.28],[127.10,37.28],[127.10,37.26]]";
    // 가운데 작은 구멍: 경도 127.105~127.115, 위도 37.265~37.275
    private static final String HOLE = "[[127.105,37.265],[127.115,37.265],[127.115,37.275],[127.105,37.275],[127.105,37.265]]";

    private JsonNode json(String s) {
        return mapper.readTree(s);
    }

    @Test
    @DisplayName("Polygon: 안이면 true, 밖이면 false")
    void polygon() {
        JsonNode geo = json("{\"type\":\"Polygon\",\"coordinates\":[" + SQUARE + "]}");
        assertThat(DongLocator.contains(geo, 127.11, 37.27)).isTrue();
        assertThat(DongLocator.contains(geo, 127.13, 37.27)).isFalse();
    }

    @Test
    @DisplayName("구멍 안은 바깥으로 쳐요")
    void hole() {
        JsonNode geo = json("{\"type\":\"Polygon\",\"coordinates\":[" + SQUARE + "," + HOLE + "]}");
        assertThat(DongLocator.contains(geo, 127.11, 37.27)).isFalse();
        assertThat(DongLocator.contains(geo, 127.101, 37.261)).isTrue();
    }

    @Test
    @DisplayName("Feature, FeatureCollection, MultiPolygon 도 읽어요")
    void wrappers() {
        String polygon = "{\"type\":\"Polygon\",\"coordinates\":[" + SQUARE + "]}";
        assertThat(DongLocator.contains(json("{\"type\":\"Feature\",\"geometry\":" + polygon + "}"), 127.11, 37.27)).isTrue();
        assertThat(DongLocator.contains(json("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":" + polygon + "}]}"), 127.11, 37.27)).isTrue();
        assertThat(DongLocator.contains(json("{\"type\":\"MultiPolygon\",\"coordinates\":[[" + SQUARE + "]]}"), 127.11, 37.27)).isTrue();
    }

    @Test
    @DisplayName("모르는 모양이면 false")
    void unknown() {
        assertThat(DongLocator.contains(json("{\"type\":\"Point\",\"coordinates\":[127.11,37.27]}"), 127.11, 37.27)).isFalse();
    }
}
