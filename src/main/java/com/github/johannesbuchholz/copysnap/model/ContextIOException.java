package com.github.johannesbuchholz.copysnap.model;

import java.io.IOException;

public class ContextIOException extends IOException {

    public ContextIOException(String message, IOException cause) {
        super(message, cause);
    }
}
