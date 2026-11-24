package com.example.calendar.booking;

import com.example.calendar.api.BookingsApi;
import com.example.calendar.model.Booking;
import com.example.calendar.model.CreateBookingRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BookingController implements BookingsApi {

  private final BookingService service;
  private final BookingRepository bookings;
  private final Clock clock;

  BookingController(BookingService service, BookingRepository bookings, Clock clock) {
    this.service = service;
    this.bookings = bookings;
    this.clock = clock;
  }

  @Override
  public ResponseEntity<Booking> createBooking(CreateBookingRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @Override
  public ResponseEntity<List<Booking>> listBookings() {
    return ResponseEntity.ok(bookings.upcoming(clock.instant()));
  }
}
