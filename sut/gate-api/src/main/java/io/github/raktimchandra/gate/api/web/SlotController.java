package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.api.service.SlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slots;
    private final Views views;

    public SlotController(SlotService slots, Views views) {
        this.slots = slots;
        this.views = views;
    }

    @GetMapping
    List<Dtos.SlotView> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) @Pattern(regexp = "[A-Z0-9-]{1,12}") String lane) {
        return slots.onDate(date, lane).stream().map(views::slot).toList();
    }

    @GetMapping("/{id}")
    Dtos.SlotView get(@PathVariable long id) {
        return views.slot(slots.get(id));
    }

    @PostMapping
    ResponseEntity<Dtos.SlotView> create(@Valid @RequestBody Dtos.CreateSlotRequest body) {
        Dtos.SlotView created = views.slot(
                slots.create(body.lane(), body.start().toInstant(), body.lengthMinutes(), body.capacity()));
        return ResponseEntity.created(URI.create("/api/slots/" + created.id())).body(created);
    }
}
