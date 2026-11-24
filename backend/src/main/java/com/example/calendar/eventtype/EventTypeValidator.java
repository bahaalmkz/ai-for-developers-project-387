package com.example.calendar.eventtype;

import com.example.calendar.error.ValidationException;
import com.example.calendar.model.CreateEventTypeRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Правила данных типа встречи (спецификация #13). */
final class EventTypeValidator {

  private static final Pattern ID = Pattern.compile("[a-z0-9-]+");

  private EventTypeValidator() {}

  static void validate(CreateEventTypeRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();

    String id = request.getId();
    if (id == null || !ID.matcher(id).matches()) {
      errors.put("id", "Только латиница в нижнем регистре, цифры и дефис");
    } else if (id.length() > 100) {
      errors.put("id", "Не длиннее 100 символов");
    }

    String title = request.getTitle();
    if (title == null || title.isBlank()) {
      errors.put("title", "Название не может быть пустым");
    } else if (title.length() > 100) {
      errors.put("title", "Название не длиннее 100 символов");
    }

    String description = request.getDescription();
    if (description != null && description.length() > 500) {
      errors.put("description", "Описание не длиннее 500 символов");
    }

    Integer duration = request.getDurationMinutes();
    if (duration == null || duration < 30 || duration > 480 || duration % 30 != 0) {
      errors.put("durationMinutes", "Длительность кратна 30, от 30 до 480 минут");
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}
