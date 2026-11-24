package com.example.calendar.health;

import com.example.calendar.api.HealthApi;
import com.example.calendar.model.HealthStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController implements HealthApi {

  @Override
  public ResponseEntity<HealthStatus> getHealth() {
    return ResponseEntity.ok(new HealthStatus().status(HealthStatus.StatusEnum.OK));
  }
}
