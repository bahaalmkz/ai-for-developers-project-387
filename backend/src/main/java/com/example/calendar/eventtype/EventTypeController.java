package com.example.calendar.eventtype;

import com.example.calendar.api.EventTypesApi;
import com.example.calendar.error.ConflictException;
import com.example.calendar.model.CreateEventTypeRequest;
import com.example.calendar.model.EventType;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventTypeController implements EventTypesApi {

  private final JdbcClient jdbc;

  EventTypeController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ResponseEntity<List<EventType>> listEventTypes() {
    List<EventType> eventTypes =
        jdbc.sql(
                "SELECT id, title, description, duration_minutes FROM event_types ORDER BY title, id")
            .query(
                (rs, rowNum) ->
                    new EventType()
                        .id(rs.getString("id"))
                        .title(rs.getString("title"))
                        .description(rs.getString("description"))
                        .durationMinutes(rs.getInt("duration_minutes")))
            .list();
    return ResponseEntity.ok(eventTypes);
  }

  @Override
  public ResponseEntity<EventType> createEventType(CreateEventTypeRequest request) {
    EventTypeValidator.validate(request);
    String description = request.getDescription() == null ? "" : request.getDescription();
    try {
      jdbc.sql(
              "INSERT INTO event_types (id, title, description, duration_minutes) VALUES (?, ?, ?, ?)")
          .params(request.getId(), request.getTitle(), description, request.getDurationMinutes())
          .update();
    } catch (DuplicateKeyException e) {
      throw new ConflictException("Тип встречи с id «" + request.getId() + "» уже существует");
    }
    EventType created =
        new EventType()
            .id(request.getId())
            .title(request.getTitle())
            .description(description)
            .durationMinutes(request.getDurationMinutes());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
