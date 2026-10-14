package io.github.raktimchandra.testharness.suites.bdd.steps;

import io.cucumber.java.ParameterType;
import java.time.LocalTime;

/** Domain vocabulary for step expressions. */
public class ParameterTypes {

    /** A 24-hour terminal wall-clock time such as {@code 09:30}. */
    @ParameterType("(?:[01]\\d|2[0-3]):[0-5]\\d")
    public LocalTime time(String value) {
        return LocalTime.parse(value);
    }
}
