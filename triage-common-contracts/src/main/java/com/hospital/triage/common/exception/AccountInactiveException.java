/** AccountInactiveException: application type for the triage hospital services. */
package com.hospital.triage.common.exception;

public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException() {
        super("This account is disabled");
    }
}
