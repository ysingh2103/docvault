package com.docvault;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application log lines. Uses Java's standard logger, which Tomcat sends to its log files
 * and to the systemd journal (journalctl -u tomcat10).
 */
final class Log {
    private static final Logger LOG = Logger.getLogger("com.docvault");

    private Log() {}

    static void log(String message) {
        LOG.info(message);
    }

    static void log(String message, Throwable error) {
        LOG.log(Level.WARNING, message, error);
    }
}
