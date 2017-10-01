package de.gekko.old;

import org.knowm.xchange.bittrex.v1.BittrexExchange;
import org.knowm.xchange.currency.CurrencyPair;

public class Old_BittrexArbitrageExchange extends Old_AbstractArbitrageExchange {

	public Old_BittrexArbitrageExchange(CurrencyPair currencyPair, String apiKey, String secretKey) {
		super(BittrexExchange.class, "BitTrex", currencyPair, apiKey, secretKey);
	}

}
