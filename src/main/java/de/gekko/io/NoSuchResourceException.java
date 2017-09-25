package de.gekko.io;

/**
 *
 * @author --
 *
 */
@SuppressWarnings("serial")
public class NoSuchResourceException extends RuntimeException {
	public NoSuchResourceException(String fileName) {
		super("Resource konnte nicht gefunden werden: " + fileName);
	}

	public NoSuchResourceException(Throwable cause) {
		super(cause);
	}
}
