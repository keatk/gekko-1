package de.gekko.enums;

public enum ExchangeType {
	
	BITFINEX ("Bitfinex"), BITTREX ("Bitrex");

	private final String displayName;

	private ExchangeType(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}

}
