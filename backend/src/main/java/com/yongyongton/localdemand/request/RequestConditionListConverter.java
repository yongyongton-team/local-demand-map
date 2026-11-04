package com.yongyongton.localdemand.request;

import jakarta.persistence.Converter;

@Converter
public class RequestConditionListConverter extends EnumListConverter<RequestCondition> {
    public RequestConditionListConverter() {
        super(RequestCondition.class);
    }
}
