package org.intocps.maestro.framework.fmi2.mablfactory;

public class MablFactoryException extends Exception {
    public MablFactoryException() {
    }

    public MablFactoryException(String message) {
        super(message);
    }

    public MablFactoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public MablFactoryException(Throwable cause) {
        super(cause);
    }

    public MablFactoryException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
