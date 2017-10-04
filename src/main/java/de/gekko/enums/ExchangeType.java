package de.gekko.enums;

public enum ExchangeType {

	BITFINEX("Bitfinex"), 
	BITSTAMP("Bitstamp"), 
	BITTREX("Bitrex"), 
	CEXIO("CexIO"), 
	GDAX("GDax"),
	POLONIEX("Poloniex"),
	KRAKEN("Kraken"),
	BTCMARKETS("BTC Markets"),
	COINFLOOR("Coinfloor");

	private final String displayName;

	private ExchangeType(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}

}
