package com.example.calendar.slot;

import java.util.List;

/**
 * Общая модель занятости: интервалы, занятые встречами любых типов. Из неё берут данные и расчёт
 * свободных слотов, и проверка пересечений при записи.
 */
public interface BusyIntervals {

  List<Interval> all();
}
