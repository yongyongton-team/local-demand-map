package com.yongyongton.localdemand.request;

import jakarta.persistence.AttributeConverter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * enum 목록 ↔ 쉼표로 이은 문자열. shop_request.conditions / time_slots 칼럼용.
 * 칼럼이 NOT NULL DEFAULT '' 라서 빈 목록은 ""로 저장해요.
 */
public abstract class EnumListConverter<E extends Enum<E>> implements AttributeConverter<List<E>, String> {

    private final Class<E> type;

    protected EnumListConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(List<E> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public List<E> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> Enum.valueOf(type, code))
                .toList();
    }
}
