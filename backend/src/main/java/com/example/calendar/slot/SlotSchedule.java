package com.example.calendar.slot;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Расписание из конфигурации (ADR 0003): один пояс, часы работы, шаг стартов, окно записи. Значения
 * по умолчанию совпадают с ADR; переопределяются свойствами {@code calendar.schedule.*}.
 */
@ConfigurationProperties(prefix = "calendar.schedule")
public record SlotSchedule(
    @DefaultValue("Asia/Almaty") ZoneId zone,
    @DefaultValue("09:00") LocalTime workStart,
    @DefaultValue("18:00") LocalTime workEnd,
    @DefaultValue("30m") Duration step,
    @DefaultValue("14") int windowDays) {}
