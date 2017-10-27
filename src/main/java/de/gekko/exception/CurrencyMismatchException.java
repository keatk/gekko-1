package de.gekko.exception;

public class CurrencyMismatchException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
    public CurrencyMismatchException () {

    }

    public CurrencyMismatchException (String message) {
        super (message);
    }

    public CurrencyMismatchException (Throwable cause) {
        super (cause);
    }

    public CurrencyMismatchException (String message, Throwable cause) {
        super (message, cause);
    }

}
