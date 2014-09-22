package com.apple.iad.rhq.alert.aggregate;

/**
 * Thrown if no aggregate data was returned.
 */
public class NoDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construct with message.
     */
    public NoDataException(String string) {
        super(string);
    }

}
