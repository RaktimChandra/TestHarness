package io.github.raktimchandra.testharness.insights.fixtures;

/** Stands in for Selenium's exception: the classifier matches by simple class name. */
public class NoSuchElementException extends RuntimeException {
    public NoSuchElementException(String message) {
        super(message);
    }
}
