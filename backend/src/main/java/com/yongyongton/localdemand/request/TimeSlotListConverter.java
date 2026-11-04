package com.yongyongton.localdemand.request;

import jakarta.persistence.Converter;

@Converter
public class TimeSlotListConverter extends EnumListConverter<TimeSlot> {
    public TimeSlotListConverter() {
        super(TimeSlot.class);
    }
}
