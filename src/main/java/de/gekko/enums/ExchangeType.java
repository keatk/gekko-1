package de.gekko.enums;

public enum ExchangeType {

	BITFINEX("Bitfinex"), BITSTAMP("Bitstamp"), BITTREX("Bitrex"), GDAX("GDax");

	private final String displayName;

	private ExchangeType(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}

}
