package com.github.johannesbuchholz.copysnap.model;

import java.io.IOException;
import java.io.UncheckedIOException;

public class ContextIOException extends UncheckedIOException {

    public ContextIOException(String message, IOException cause) {
        super(message, cause);
    }
}
