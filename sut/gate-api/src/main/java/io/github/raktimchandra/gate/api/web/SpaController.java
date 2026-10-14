package io.github.raktimchandra.gate.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the single-page UI for its client-side routes (deep links and refreshes). */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/driver", "/driver/**", "/clerk", "/clerk/**"})
    String index() {
        return "forward:/index.html";
    }
}
