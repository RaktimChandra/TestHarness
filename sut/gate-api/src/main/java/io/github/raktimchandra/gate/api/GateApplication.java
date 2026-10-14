package io.github.raktimchandra.gate.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Gate Appointment: truck drivers book gate slots; clerks check trucks in. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GateApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateApplication.class, args);
    }
}
