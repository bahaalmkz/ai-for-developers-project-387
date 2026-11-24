package com.example.calendar.error;

import com.example.calendar.model.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Единый формат ошибок {code, message} для всех эндпоинтов. */
@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ApiError> handleValidation(ValidationException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiError()
                .code("validation_error")
                .message(e.getMessage())
                .fieldErrors(e.fieldErrors()));
  }

  /** Обязательное поле отсутствует: проверка из контракта срабатывает до ручных валидаторов. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException e) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), "Обязательное поле"));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiError()
                .code("validation_error")
                .message("Ошибка данных")
                .fieldErrors(fieldErrors));
  }

  /** Нечитаемый JSON или значение неверного типа (например, дата не в формате ISO 8601). */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> handleUnreadable(Exception e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiError()
                .code("validation_error")
                .message("Не удалось разобрать запрос: проверьте формат данных"));
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> handleConflict(ConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError().code("conflict").message(e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handle(Exception e) {
    // Ошибки самого Spring MVC (404, 405, 400 и т. п.) сохраняют свой статус
    if (e instanceof ErrorResponse errorResponse) {
      HttpStatusCode status = errorResponse.getStatusCode();
      HttpStatus known = HttpStatus.resolve(status.value());
      String code = known == null ? "error" : known.name().toLowerCase();
      String message = known == null ? "Ошибка запроса" : russianMessage(known);
      return ResponseEntity.status(status).body(new ApiError().code(code).message(message));
    }
    log.error("Необработанная ошибка", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError().code("internal_error").message("Внутренняя ошибка сервера"));
  }

  private static String russianMessage(HttpStatus status) {
    return switch (status) {
      case NOT_FOUND -> "Не найдено";
      case METHOD_NOT_ALLOWED -> "Метод не поддерживается";
      case UNSUPPORTED_MEDIA_TYPE -> "Неподдерживаемый тип содержимого";
      default -> "Ошибка запроса";
    };
  }
}
