package com.danielfrak.code.keycloak.providers.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LogCapture extends Handler implements AutoCloseable {

    private final Logger logger;
    private final List<String> messages = new ArrayList<>();

    public LogCapture(Class<?> loggingClass) {
        logger = Logger.getLogger(loggingClass.getName());
        logger.addHandler(this);
    }

    public List<String> messages() {
        return messages;
    }

    @Override
    public void publish(LogRecord logRecord) {
        messages.add(logRecord.getMessage());
    }

    @Override
    public void flush() {
        // Nothing to flush
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }
}
