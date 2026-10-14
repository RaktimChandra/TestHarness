package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.api.security.CurrentActor;
import io.github.raktimchandra.gate.api.service.BookingService;
import io.github.raktimchandra.gate.domain.BookingStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookings;
    private final Views views;

    public BookingController(BookingService bookings, Views views) {
        this.bookings = bookings;
        this.views = views;
    }

    @PostMapping
    ResponseEntity<Dtos.BookingView> book(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody Dtos.CreateBookingRequest body) {
        Dtos.BookingView created = views.booking(
                bookings.book(CurrentActor.from(jwt), body.slotId(), body.containerNumber(), body.direction()));
        return ResponseEntity.created(URI.create("/api/bookings/" + created.reference())).body(created);
    }

    @GetMapping
    List<Dtos.BookingView> search(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) String containerNumber,
                                  @RequestParam(required = false) String reference,
                                  @RequestParam(required = false) BookingStatus status,
                                  @RequestParam(required = false) String driver,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                  LocalDate date) {
        return bookings.search(CurrentActor.from(jwt), containerNumber, reference, status, driver, date).stream()
                .map(views::booking).toList();
    }

    @GetMapping("/{reference}")
    Dtos.BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return views.booking(bookings.get(CurrentActor.from(jwt), reference));
    }

    @PutMapping("/{reference}/slot")
    Dtos.BookingView reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference,
                                @Valid @RequestBody Dtos.RescheduleRequest body) {
        return views.booking(bookings.reschedule(CurrentActor.from(jwt), reference, body.slotId()));
    }

    @PostMapping("/{reference}/cancel")
    Dtos.BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return views.booking(bookings.cancel(CurrentActor.from(jwt), reference));
    }

    @PostMapping("/{reference}/check-in")
    Dtos.BookingView checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return views.booking(bookings.checkIn(CurrentActor.from(jwt), reference));
    }
}
