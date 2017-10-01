package de.gekko.exchanges;

import org.knowm.xchange.bitstamp.BitstampExchange;

public class BitstampArbitragerExchange extends AbstractArbitrageExchange {
	
	public BitstampArbitragerExchange(String apiKey, String secretKey) {
		super(BitstampExchange.class, apiKey, secretKey);
	}

}
