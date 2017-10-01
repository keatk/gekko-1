package de.gekko.exchanges;

import org.knowm.xchange.bittrex.v1.BittrexExchange;

public class BittrexArbitrageExchange extends AbstractArbitrageExchange {

	public BittrexArbitrageExchange(String apiKey, String secretKey) {
		super(BittrexExchange.class, apiKey, secretKey);
	}

}
