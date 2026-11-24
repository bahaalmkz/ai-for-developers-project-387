package com.example.calendar.error;

/** Конфликт с текущим состоянием данных. Превращается в 409. */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
