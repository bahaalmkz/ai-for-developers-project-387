package com.example.calendar.error;

import java.util.Map;

/** Ошибка данных запроса: поле → сообщение. Превращается в 400 с fieldErrors. */
public class ValidationException extends RuntimeException {

  private final Map<String, String> fieldErrors;

  public ValidationException(Map<String, String> fieldErrors) {
    super("Ошибка данных");
    this.fieldErrors = fieldErrors;
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }
}
